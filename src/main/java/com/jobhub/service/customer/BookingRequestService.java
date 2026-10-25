package com.jobhub.service.customer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.customer.CustomerDiscoveryApi.*;
import com.jobhub.dto.job.*;
import com.jobhub.dto.job.BookingRequestsApi.*;
import com.jobhub.entity.job.*;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.exception.*;
import com.jobhub.payments.PayHereBookingCheckout;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.job.*;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class BookingRequestService {
    private final BookingRequestRepository requests;
    private final BookingOfferRepository offers;
    private final BookingRequestPolicyRepository policies;
    private final BookingDispatchEventRepository events;
    private final BookingPaymentNoticeRepository notices;
    private final ServiceOptionRepository options;
    private final ServiceProviderRepository providers;
    private final MarketRepository markets;
    private final UserRepository users;
    private final JobRepository jobs;
    private final CustomerDiscoveryService discovery;
    private final CustomerBookingService bookings;
    private final PayHereBookingCheckout checkout;
    private final BookingCodeCipher codes;
    private final ObjectMapper mapper;
    private final com.jobhub.service.audit.AuditService audit;
    private final com.jobhub.service.finance.WalletService wallets;

    @Transactional
    public Policy setPolicy(Long optionId, Policy input) {
        var option=options.findLockedById(optionId).orElseThrow(() -> new ResourceNotFoundException("Option not found"));
        if(input.enabled()) {
            requireSupported(option); checkout.requireReady(); codes.requireReady();
        }
        var p=policies.findById(optionId).orElseGet(BookingRequestPolicy::new);
        p.setOptionId(optionId); p.setEnabled(input.enabled()); p.setMinimumNoticeMinutes(input.minimumNoticeMinutes());
        p.setOfferTimeoutSeconds(input.offerTimeoutSeconds()); p.setConfirmationTimeoutSeconds(input.confirmationTimeoutSeconds());
        p.setMatchingTimeoutSeconds(input.matchingTimeoutSeconds()); p.setMaximumRadiusKm(input.maximumRadiusKm());
        policies.save(p); return policyView(p);
    }
    @Transactional(readOnly=true)
    public Policy policy(Long optionId) {
        if(!options.existsById(optionId)) throw new ResourceNotFoundException("Option not found");
        var p=policies.findById(optionId).orElseGet(BookingRequestPolicy::new);
        return policyView(p);
    }
    private Policy policyView(BookingRequestPolicy p) {
        return new Policy(p.isEnabled(),p.getMinimumNoticeMinutes(),p.getOfferTimeoutSeconds(),
                p.getConfirmationTimeoutSeconds(),p.getMatchingTimeoutSeconds(),p.getMaximumRadiusKm());
    }
    @Transactional
    public View create(Long userId,String key,Create input) {
        if(key==null || key.isBlank() || key.length()>100) throw new IllegalArgumentException("Idempotency-Key of 1–100 characters is required");
        // Serialize all creates for this customer, including concurrent retries before a row exists.
        users.lockById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String payload=write(input), fingerprint=sha256(payload);
        var existing=requests.findByUserIdAndIdempotencyKey(userId,key);
        if(existing.isPresent()) {
            if(!fingerprint.equals(existing.get().getPayloadHash())) throw new ConflictException("Idempotency key was used with different inputs");
            return view(existing.get());
        }
        var b=input.booking();
        if(requests.existsByUserIdAndOptionIdAndStatusIn(userId,b.optionId(),
                Set.of("MATCHING","AWAITING_CUSTOMER_CONFIRMATION","PAYMENT_PENDING","PAYMENT_RECONCILIATION")))
            throw new ConflictException("Resolve or cancel the existing active request for this option before creating another");
        var option=options.findByOptionIdAndServiceProviderTypeId(b.optionId(),b.serviceTypeId())
                .filter(o -> o.activeForService(b.serviceTypeId())).orElseThrow(() -> new ResourceNotFoundException("Active option not found"));
        requireSupported(option); checkout.requireReady(); codes.requireReady();
        var policy=policies.findById(option.getOptionId()).filter(BookingRequestPolicy::isEnabled)
                .orElseThrow(() -> new ConflictException("Booking requests are not enabled for this option"));
        if(!"CARD".equals(b.paymentMethod())) throw new IllegalArgumentException("Booking requests require CARD");
        if(b.providerIds()!=null && !b.providerIds().isEmpty()) throw new IllegalArgumentException("This MVP supports one provider per request");
        boolean direct="DIRECT_REQUEST".equals(option.getProviderSelectionMode());
        if(direct != (b.providerId()!=null)) throw new IllegalArgumentException("DIRECT_REQUEST requires providerId; OPEN_REQUEST must omit it");
        bookings.validateRequestFields(userId,b);
        var market=markets.findById(input.marketId()).orElseThrow(() -> new ResourceNotFoundException("Market not found"));
        ZoneId zone=ZoneId.of(market.getTimezone());
        boolean route="ROUTE_BASED".equals(option.getSchedulingModel());
        LocalDateTime start=route?b.requestedPickupTime():b.startDatetime();
        Instant starts=start.atZone(zone).toInstant();
        if(starts.isBefore(Instant.now().plusSeconds(policy.getMinimumNoticeMinutes()*60L)))
            throw new IllegalArgumentException("Choose a pickup/start after this option's minimum advance notice");
        BigDecimal radius=input.radiusKm()==null?policy.getMaximumRadiusKm():input.radiusKm();
        if(radius.compareTo(policy.getMaximumRadiusKm())>0) throw new IllegalArgumentException("Search radius exceeds option policy");
        Point location=null;
        if(!route && !"ONLINE".equals(b.deliveryMode())) {
            if("PROVIDER_TO_CUSTOMER".equals(b.deliveryMode()) && b.meetingPoint()!=null)
                location=new Point(b.meetingPoint().latitude(),b.meetingPoint().longitude());
            else location=input.searchLocation();
        }
        // Pricing choices must also constrain dispatch so every offered provider supports them.
        var filters=new ArrayList<ProviderFilter>(input.providerFilters()==null?List.of():input.providerFilters());
        if(b.pricingSelections()!=null) for(var selection:b.pricingSelections()) {
            var filter=filters.stream().filter(f -> f.fieldId().equals(selection.fieldId())).findFirst();
            var exact=mapper.createArrayNode().add(selection.choiceValue());
            if(filter.isPresent() && !exact.equals(filter.get().value()))
                throw new IllegalArgumentException("Pricing choices must match the single selected provider filter value");
            if(filter.isEmpty()) filters.add(new ProviderFilter(selection.fieldId(),exact,null,null));
        }
        var search=new Search(input.marketId(),b.serviceTypeId(),b.optionId(),b.deliveryMode(),start.atZone(zone).toOffsetDateTime(),
                route?null:b.expectedEndDatetime().atZone(zone).toOffsetDateTime(),location,
                route?new Point(b.pickup().latitude(),b.pickup().longitude()):null,
                route?new Point(b.destination().latitude(),b.destination().longitude()):null,
                radius,input.languages(),input.requiredSkillIds(),0,100,filters);
        var matches=(direct?discovery.searchForProvider(userId,search,b.providerId()):discovery.search(userId,search)).candidates().stream()
                .filter(c -> !direct || b.providerId().equals(c.providerId()))
                .filter(c -> b.providerLocationId()==null || b.providerLocationId().equals(c.providerLocationId())).toList();
        var unique=new LinkedHashMap<Long,Candidate>(); matches.forEach(c -> unique.putIfAbsent(c.providerId(),c));
        var candidates=List.copyOf(unique.values());
        var request=new BookingRequest(); request.setUserId(userId); request.setIdempotencyKey(key); request.setPayloadHash(fingerprint);
        request.setMarketId(input.marketId()); request.setOptionId(b.optionId()); request.setSelectionMode(option.getProviderSelectionMode());
        request.setPayloadJson(payload); request.setCandidatesJson(write(candidates)); request.setStatus("MATCHING"); request.setPaymentStatus("NOT_STARTED");
        request.setExpiresAt(Instant.now().plusSeconds(policy.getMatchingTimeoutSeconds()));
        requests.saveAndFlush(request);
        dispatch(request,policy);
        return view(request);
    }
    private void requireSupported(ServiceOption option) {
        if(!Set.of("DIRECT_REQUEST","OPEN_REQUEST").contains(option.getProviderSelectionMode())
                || "ONE_CUSTOMER_MANY_PROVIDERS".equals(option.getFulfillmentModel())
                || option.getRequiredProviderCount()!=null && option.getRequiredProviderCount()>1)
            throw new IllegalArgumentException("Request MVP supports single-provider fulfillment only");
        if(!option.getAllowedPaymentMethods().contains("CARD") || !"ADMIN".equals(option.getPricingOwner()))
            throw new IllegalArgumentException("Request MVP requires CARD enabled and ADMIN pricing");
    }
    private CreateBookingRequest selected(BookingRequest r,Long providerId,Long locationId) {
        var b=read(r.getPayloadJson(),Create.class).booking();
        return new CreateBookingRequest(b.serviceTypeId(),b.optionId(),providerId,null,b.deliveryMode(),b.startDatetime(),
                b.expectedEndDatetime(),b.requestedPickupTime(),b.pickup(),b.destination(),locationId,b.meetingPoint(),
                b.customerNote(),b.answers(),"CARD",b.pricingSelections());
    }
    private void dispatch(BookingRequest r,BookingRequestPolicy p) {
        List<Candidate> candidates=readCandidates(r.getCandidatesJson());
        // One offer at a time; the persisted queue is bounded to the first 100 nearest candidate rows.
        if(!p.isEnabled() || !Instant.now().isBefore(r.getExpiresAt()) || r.getNextCandidate()>=candidates.size()) {
            r.setStatus("UNMATCHED"); event(r,r.getUserId(),null,"BOOKING_UNMATCHED",null); return;
        }
        Candidate candidate=null;
        while(r.getNextCandidate()<candidates.size()) {
            var next=candidates.get(r.getNextCandidate());r.setNextCandidate(r.getNextCandidate()+1);
            try {
                var quote=bookings.quote(r.getUserId(),selected(r,next.providerId(),next.providerLocationId()));
                if(!"LKR".equals(quote.currencyCode()) || quote.expectedAmount().signum()<=0) continue;
                r.setQuotedAmount(quote.expectedAmount());r.setCurrencyCode(quote.currencyCode());
                r.setPlatformFeePercentage(quote.platformFeePercentage());r.setPlatformFee(quote.platformFee());
                r.setProviderEarnings(quote.providerEarnings());candidate=next;break;
            } catch(IllegalArgumentException|ResourceNotFoundException|ConflictException|CustomerEligibilityException unavailable) {
                // The quote uses a separate transaction, so validation cannot poison dispatch.
            }
        }
        if(candidate==null) {r.setStatus("UNMATCHED");event(r,r.getUserId(),null,"BOOKING_UNMATCHED",null);return;}
        var offer=new BookingOffer(); offer.setRequestId(r.getRequestId()); offer.setProviderId(candidate.providerId());
        offer.setProviderLocationId(candidate.providerLocationId()); offer.setStatus("PENDING");
        offer.setExpiresAt(Instant.now().plusSeconds(p.getOfferTimeoutSeconds()).isBefore(r.getExpiresAt())
                ?Instant.now().plusSeconds(p.getOfferTimeoutSeconds()):r.getExpiresAt());
        offers.saveAndFlush(offer);
        var provider=providers.findById(candidate.providerId()).orElseThrow();
        event(r,provider.getUserId(),offer.getOfferId(),"BOOKING_OFFER",offer.getExpiresAt());
    }
    @Transactional
    public View get(Long userId,Long id) { var r=owned(userId,id); tick(r); return view(r); }
    @Transactional
    public List<Offer> providerOffers(Long userId) {
        Long providerId=providerId(userId);
        var result=new ArrayList<Offer>();
        for(var offer:offers.findAllByProviderIdAndStatusOrderByOfferIdAsc(providerId,"PENDING")) {
            var r=locked(offer.getRequestId()); tick(r);
            if(!"MATCHING".equals(r.getStatus()) || !"PENDING".equals(offer.getStatus()) || !Instant.now().isBefore(offer.getExpiresAt())) continue;
            var b=read(r.getPayloadJson(),Create.class).booking();
            result.add(new Offer(offer.getOfferId(),r.getRequestId(),offer.getStatus(),offer.getExpiresAt(),b.serviceTypeId(),b.optionId(),
                    b.deliveryMode(),b.startDatetime(),b.expectedEndDatetime(),b.requestedPickupTime(),offer.getProviderLocationId(),r.getQuotedAmount(),r.getCurrencyCode(),
                    b.pickup(),b.destination(),b.meetingPoint(),r.getPlatformFeePercentage(),r.getPlatformFee(),r.getProviderEarnings()));
        }
        return result;
    }
    @Transactional
    public View accept(Long userId,Long offerId) {
        var initial=offers.findById(offerId).orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
        Long providerId=providerId(userId);
        if(!providerId.equals(initial.getProviderId())) throw new ResourceNotFoundException("Offer not found");
        var r=locked(initial.getRequestId());
        var offer=offers.findById(offerId).orElseThrow();
        if("ACCEPTED".equals(offer.getStatus()) && r.getJobId()!=null) return view(r);
        if(!"MATCHING".equals(r.getStatus()) || !"PENDING".equals(offer.getStatus())
                || !Instant.now().isBefore(offer.getExpiresAt()) || !Instant.now().isBefore(r.getExpiresAt()))
            throw new ConflictException("Offer is no longer available");
        var policy=policies.findById(r.getOptionId()).filter(BookingRequestPolicy::isEnabled)
                .orElseThrow(() -> new ConflictException("Booking requests disabled for this option"));
        var job=bookings.reserveCardRequest(r.getUserId(),selected(r,providerId,offer.getProviderLocationId()));
        if(r.getQuotedAmount()==null || r.getQuotedAmount().compareTo(job.getExpectedAmount())!=0
                || !Objects.equals(r.getCurrencyCode(),job.getCurrencyCode())
                || zero(r.getPlatformFeePercentage()).compareTo(zero(job.getPlatformFeePercentage()))!=0
                || zero(r.getPlatformFee()).compareTo(zero(job.getPlatformFee()))!=0
                || net(r.getQuotedAmount(),r.getPlatformFee(),r.getProviderEarnings()).compareTo(
                        net(job.getExpectedAmount(),job.getPlatformFee(),job.getProviderEarnings()))!=0)
            throw new ConflictException("The offered price or platform fee changed; cancel this request and request a new quote");
        var providerMarket=job.getMarketId();
        if(!r.getMarketId().equals(providerMarket)) throw new ConflictException("Provider market changed; search again");
        r.setJobId(job.getJobId()); r.setQuotedAmount(job.getExpectedAmount()); r.setCurrencyCode(job.getCurrencyCode());
        r.setPlatformFeePercentage(zero(job.getPlatformFeePercentage()));r.setPlatformFee(zero(job.getPlatformFee()));
        r.setProviderEarnings(net(job.getExpectedAmount(),job.getPlatformFee(),job.getProviderEarnings()));
        if(!"LKR".equals(job.getCurrencyCode())) throw new IllegalArgumentException("Only LKR is supported");
        offer.setStatus("ACCEPTED"); r.setStatus("AWAITING_CUSTOMER_CONFIRMATION");
        r.setConfirmationExpiresAt(Instant.now().plusSeconds(policy.getConfirmationTimeoutSeconds()));
        event(r,r.getUserId(),offerId,"PROVIDER_ACCEPTED",r.getConfirmationExpiresAt());
        event(r,userId,offerId,"OFFER_ACCEPTED",null);
        return view(r);
    }
    @Transactional(readOnly=true)
    public BookingResponse providerBooking(Long userId,Long jobId) {
        var request=requests.findByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        var job=jobs.findById(request.getJobId()).orElseThrow();
        if(!providerId(userId).equals(job.getAssignedServiceProviderId())) throw new ResourceNotFoundException("Booking not found");
        return bookings.dispatchResponse(job);
    }
    @Transactional
    public View decline(Long userId,Long offerId) {
        var initial=offers.findById(offerId).orElseThrow(() -> new ResourceNotFoundException("Offer not found"));
        if(!providerId(userId).equals(initial.getProviderId())) throw new ResourceNotFoundException("Offer not found");
        var r=locked(initial.getRequestId()); var offer=offers.findById(offerId).orElseThrow();
        if("DECLINED".equals(offer.getStatus())) return view(r);
        if(!"MATCHING".equals(r.getStatus()) || !"PENDING".equals(offer.getStatus())) throw new ConflictException("Offer no longer pending");
        offer.setStatus("DECLINED");event(r,userId,offerId,"OFFER_DECLINED",null);
        dispatch(r,policies.findById(r.getOptionId()).orElseThrow()); return view(r);
    }
    @Transactional
    public Checkout confirm(Long userId,Long id,Confirm contact) {
        var r=owned(userId,id);
        if("PAYMENT_PENDING".equals(r.getStatus())) {
            if(!contact.cardMethod().equals(r.getCheckoutMethod())) throw new ConflictException("Checkout already started with another card method");
            return read(r.getCheckoutJson(),Checkout.class);
        }
        if(!"AWAITING_CUSTOMER_CONFIRMATION".equals(r.getStatus()) || !Instant.now().isBefore(r.getConfirmationExpiresAt()))
            throw new ConflictException("Customer confirmation expired or provider has not accepted");
        var job=jobs.lockPaymentJob(r.getJobId()).orElseThrow();
        var zone=ZoneId.of(markets.findById(job.getMarketId()).orElseThrow().getTimezone());
        if(!job.getStartDatetime().atZone(zone).toInstant().isAfter(Instant.now())) throw new ConflictException("Booking start time has passed");
        r.setPaymentOrderId("BR-"+UUID.randomUUID()); r.setCheckoutMethod(contact.cardMethod());
        var session=checkout.checkout(r,job,contact);
        r.setPaymentFundingMode(checkout.fundingMode());
        r.setCheckoutJson(write(session)); r.setStatus("PAYMENT_PENDING"); r.setPaymentStatus("PENDING"); r.setPaymentStartedAt(Instant.now());
        return session;
    }
    @Transactional
    public View cancel(Long userId,Long id) {
        var r=owned(userId,id);
        if("CANCELLED".equals(r.getStatus())) return view(r);
        if(!Set.of("MATCHING","AWAITING_CUSTOMER_CONFIRMATION","UNMATCHED","EXPIRED").contains(r.getStatus()))
            throw new ConflictException("Payment or confirmed booking requires reconciliation/refund; cancellation is not available here");
        stop(r,"CANCELLED"); return view(r);
    }
    @Transactional
    public void notification(Map<String,String> params) {
        checkout.verify(params);
        var found=requests.findByPaymentOrderId(params.get("order_id")).orElseThrow(() -> new ResourceNotFoundException("Payment order not found"));
        var r=locked(found.getRequestId()); var job=jobs.lockPaymentJob(r.getJobId()).orElseThrow();
        if(!job.getCurrencyCode().equals(params.get("payhere_currency"))
                || job.getExpectedAmount().compareTo(new BigDecimal(params.get("payhere_amount")))!=0)
            throw new IllegalArgumentException("Payment amount or currency mismatch");
        String status=params.get("status_code");
        if(!Set.of("2","0","-1","-2","-3").contains(status)) throw new IllegalArgumentException("Unknown payment status");
        var previous=notices.findByPaymentIdAndStatusCode(params.get("payment_id"),status);
        if(previous.isPresent()) {
            if(!r.getRequestId().equals(previous.get().getRequestId())) throw new ConflictException("Payment belongs to another order");
            return;
        }
        var notice=new BookingPaymentNotice();notice.setRequestId(r.getRequestId());notice.setPaymentId(params.get("payment_id"));
        notice.setStatusCode(status);notice.setReceivedAt(Instant.now());notices.saveAndFlush(notice);
        if("-3".equals(status)) {
            if(r.getGatewayPaymentId()!=null && !r.getGatewayPaymentId().equals(params.get("payment_id"))) {
                event(r,r.getUserId(),null,"DUPLICATE_PAYMENT_REVIEW",null);return;
            }
            r.setPaymentStatus("CHARGEDBACK"); job.setPaymentStatus("CHARGEDBACK");
            wallets.freezeCardSettlement(r.getUserId(),job.getJobId());
            event(r,r.getUserId(),null,"PAYMENT_REVIEW_REQUIRED",null); return;
        }
        if("2".equals(status)) {
            if("CHARGEDBACK".equals(r.getPaymentStatus())) return;
            if("PAID".equals(r.getPaymentStatus())) {
                if(!params.get("payment_id").equals(r.getGatewayPaymentId())) {
                    event(r,r.getUserId(),null,"DUPLICATE_PAYMENT_REVIEW",null);
                }
                return;
            }
            if(!Set.of("VISA","MASTER").contains(params.get("method"))) {
                r.setPaymentStatus("REFUND_REVIEW"); r.setGatewayPaymentId(params.get("payment_id"));
                job.setPaymentStatus("PAID_REVIEW"); stop(r,"PAYMENT_REVIEW"); return;
            }
            if(!Set.of("PAYMENT_PENDING","PAYMENT_RECONCILIATION").contains(r.getStatus())
                    || !"AWAITING_PAYMENT".equals(job.getJobStatus()) || "CHARGEDBACK".equals(r.getPaymentStatus())) {
                r.setPaymentStatus("REFUND_REVIEW"); r.setGatewayPaymentId(params.get("payment_id"));
                job.setPaymentStatus("PAID_REVIEW"); stop(r,"PAYMENT_REVIEW"); return;
            }
            var zone=ZoneId.of(markets.findById(job.getMarketId()).orElseThrow().getTimezone());
            if(!job.getStartDatetime().atZone(zone).toInstant().isAfter(Instant.now())) {
                r.setPaymentStatus("REFUND_REVIEW"); r.setGatewayPaymentId(params.get("payment_id"));
                job.setPaymentStatus("PAID_REVIEW"); stop(r,"PAYMENT_REVIEW"); return;
            }
            r.setGatewayPaymentId(params.get("payment_id")); r.setPaymentStatus("PAID"); r.setStatus("CONFIRMED");
            job.setPaymentStatus("PAID"); job.setJobStatus("CONFIRMED");
            r.setStartCode(codes.encrypt(codes.generate()));
            r.setCodeExpiresAt(job.getExpectedEndDatetime().atZone(zone).toInstant().plus(Duration.ofHours(24)));
            event(r,r.getUserId(),null,"BOOKING_CONFIRMED",null);
            event(r,providers.findById(job.getAssignedServiceProviderId()).orElseThrow().getUserId(),null,"BOOKING_CONFIRMED",null);
        } else if(Set.of("-1","-2").contains(status) && "PAYMENT_PENDING".equals(r.getStatus())) {
            r.setGatewayPaymentId(params.get("payment_id")); r.setPaymentStatus("FAILED"); job.setPaymentStatus("FAILED"); stop(r,"PAYMENT_FAILED");
        }
        // Pending or stale failure callbacks never undo a successful payment.
    }
    @Transactional
    public Codes customerCodes(Long userId,Long jobId) {
        var r=requests.findByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking request not found"));
        if(!userId.equals(r.getUserId())) throw new ResourceNotFoundException("Booking request not found");
        r=locked(r.getRequestId()); var job=jobs.findById(jobId).orElseThrow(); requireCodeWindow(r,job);
        if("CONFIRMED".equals(r.getStatus())) return new Codes("START",codes.decrypt(r.getStartCode()),r.getCodeExpiresAt());
        throw new ConflictException("No verification code is available in this booking state");
    }
    @Transactional
    public Transition verifyStartCode(Long userId,Long jobId,String value) {
        var found=requests.findByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking request not found"));
        var r=locked(found.getRequestId()); var job=jobs.lockPaymentJob(jobId).orElseThrow();
        if(!providerId(userId).equals(job.getAssignedServiceProviderId())) throw new ResourceNotFoundException("Booking not found");
        String expected="CONFIRMED", next="IN_PROGRESS";
        // Repeated successful transitions are idempotent; no second code can trigger another transition.
        if(next.equals(r.getStatus())) return new Transition(true,next);
        if(!expected.equals(r.getStatus()) || !"PAID".equals(job.getPaymentStatus())) throw new ConflictException("Booking cannot transition in its current state");
        requireCodeWindow(r,job);
        int attempts=r.getStartAttempts();
        if(attempts>=5) return new Transition(false,"CODE_LOCKED");
        if(!codes.matches(r.getStartCode(),value)) {
            r.setStartAttempts(attempts+1);
            return new Transition(false,attempts+1>=5?"CODE_LOCKED":"INVALID_CODE");
        }
        r.setStatus(next);job.setJobStatus(next);
        var zone=ZoneId.of(markets.findById(job.getMarketId()).orElseThrow().getTimezone());
        job.setActualStartDatetime(LocalDateTime.now(zone));r.setStartCode(null);
        event(r,r.getUserId(),null,"BOOKING_STARTED",null);
        return new Transition(true,next);
    }
    @Transactional
    public Transition requestCompletion(Long userId,Long jobId) {
        var found=requests.findByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking request not found"));
        var r=locked(found.getRequestId());var job=jobs.lockPaymentJob(jobId).orElseThrow();
        if(!providerId(userId).equals(job.getAssignedServiceProviderId())) throw new ResourceNotFoundException("Booking not found");
        if(Set.of("AWAITING_COMPLETION_CONFIRMATION","COMPLETED").contains(r.getStatus()))return new Transition(true,r.getStatus());
        if(!"IN_PROGRESS".equals(r.getStatus()) || !"IN_PROGRESS".equals(job.getJobStatus()) || !"PAID".equals(job.getPaymentStatus()))
            throw new ConflictException("Only an in-progress paid booking can request completion");
        r.setStatus("AWAITING_COMPLETION_CONFIRMATION");job.setJobStatus("AWAITING_COMPLETION_CONFIRMATION");
        var zone=ZoneId.of(markets.findById(job.getMarketId()).orElseThrow().getTimezone());
        job.setActualEndDatetime(LocalDateTime.now(zone));
        event(r,r.getUserId(),null,"COMPLETION_REQUESTED",null);
        event(r,userId,null,"COMPLETION_REQUESTED",null);
        return new Transition(true,r.getStatus());
    }
    @Transactional
    public Transition confirmCompletion(Long userId,Long jobId) {
        var r=requests.lockByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking request not found"));
        if(!userId.equals(r.getUserId()))throw new ResourceNotFoundException("Booking not found");
        var job=jobs.lockPaymentJob(jobId).orElseThrow();
        if("COMPLETED".equals(r.getStatus())){
            wallets.settleCardBooking(userId,jobId);return new Transition(true,"COMPLETED");
        }
        if(!"AWAITING_COMPLETION_CONFIRMATION".equals(r.getStatus())
                || !"AWAITING_COMPLETION_CONFIRMATION".equals(job.getJobStatus()) || !"PAID".equals(job.getPaymentStatus()))
            throw new ConflictException("The provider must request completion before the customer confirms");
        r.setStatus("COMPLETED");job.setJobStatus("COMPLETED");
        wallets.settleCardBooking(userId,jobId);
        event(r,r.getUserId(),null,"BOOKING_COMPLETED",null);
        event(r,providers.findById(job.getAssignedServiceProviderId()).orElseThrow().getUserId(),null,"BOOKING_COMPLETED",null);
        return new Transition(true,"COMPLETED");
    }
    @Transactional
    public void resetCode(Long actorId,Long jobId,String reason) {
        var found=requests.findByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking request not found"));
        var r=locked(found.getRequestId());var job=jobs.lockPaymentJob(jobId).orElseThrow();
        if(!"CONFIRMED".equals(r.getStatus()) || !"PAID".equals(job.getPaymentStatus()))
            throw new ConflictException("Only a paid booking waiting to start can receive a replacement start code");
        String replacement=codes.encrypt(codes.generate());
        r.setStartCode(replacement);r.setStartAttempts(0);
        r.setCodeExpiresAt(Instant.now().plus(Duration.ofHours(24)));
        audit.record(actorId,"BOOKING_CODE_RESET","BOOKING_REQUEST",r.getRequestId(),null,Map.of("reason",reason,"jobId",jobId,"state",r.getStatus()));
        event(r,r.getUserId(),null,"BOOKING_CODE_REPLACED",null);
    }
    @Transactional
    public View resolveUnpaid(Long actorId,Long requestId,String evidence) {
        var r=locked(requestId);
        if(!Set.of("PAYMENT_PENDING","PAYMENT_RECONCILIATION").contains(r.getStatus())
                || notices.existsByRequestIdAndStatusCode(requestId,"2"))
            throw new ConflictException("Only an outstanding payment without a recorded success can be resolved as unpaid");
        String old=r.getStatus();r.setPaymentStatus("UNPAID_REVIEWED");
        jobs.lockPaymentJob(r.getJobId()).orElseThrow().setPaymentStatus("UNPAID_REVIEWED");stop(r,"PAYMENT_FAILED");
        audit.record(actorId,"BOOKING_PAYMENT_REVIEWED_UNPAID","BOOKING_REQUEST",requestId,Map.of("status",old),
                Map.of("evidence",evidence,"orderId",r.getPaymentOrderId()));
        // Late success is recorded for refund review, never assigned after capacity was released.
        return view(r);
    }
    private void requireCodeWindow(BookingRequest r,Job job) {
        var zone=ZoneId.of(markets.findById(job.getMarketId()).orElseThrow().getTimezone());
        if(r.getCodeExpiresAt()==null || !Instant.now().isBefore(r.getCodeExpiresAt())
                || Instant.now().isBefore(job.getStartDatetime().atZone(zone).toInstant().minus(Duration.ofMinutes(30))))
            throw new ConflictException("Verification code is outside its service window");
    }
    @Transactional(readOnly=true)
    public List<BookingDispatchEvent> events(Long userId,Long after) {
        return events.findTop100ByUserIdAndEventIdGreaterThanOrderByEventIdAsc(userId,after);
    }
    @Transactional
    public void expire(Long requestId) { tick(locked(requestId)); }
    private void tick(BookingRequest r) {
        if("MATCHING".equals(r.getStatus())) {
            var pending=offers.findAllByRequestIdOrderByOfferIdAsc(r.getRequestId()).stream().filter(o -> "PENDING".equals(o.getStatus())).toList();
            if(!Instant.now().isBefore(r.getExpiresAt())) {stop(r,"UNMATCHED");return;}
            if(!pending.isEmpty() && !Instant.now().isBefore(pending.get(0).getExpiresAt())) {
                pending.get(0).setStatus("EXPIRED");
                event(r,providers.findById(pending.get(0).getProviderId()).orElseThrow().getUserId(),pending.get(0).getOfferId(),"OFFER_EXPIRED",null);
                dispatch(r,policies.findById(r.getOptionId()).orElseThrow());
            }
        } else if("AWAITING_CUSTOMER_CONFIRMATION".equals(r.getStatus()) && !Instant.now().isBefore(r.getConfirmationExpiresAt())) stop(r,"EXPIRED");
        else if("PAYMENT_PENDING".equals(r.getStatus()) && Instant.now().isAfter(r.getPaymentStartedAt().plus(Duration.ofMinutes(15)))) {
            r.setStatus("PAYMENT_RECONCILIATION");
            event(r,r.getUserId(),null,"PAYMENT_REVIEW_REQUIRED",null);
            // Keep capacity: a hosted checkout can still produce a late successful payment.
        }
    }
    private void stop(BookingRequest r,String status) {
        r.setStatus(status);
        for(var offer:offers.findAllByRequestIdOrderByOfferIdAsc(r.getRequestId())) {
            if(Set.of("PENDING","ACCEPTED").contains(offer.getStatus())) {
                offer.setStatus("CANCELLED");
                event(r,providers.findById(offer.getProviderId()).orElseThrow().getUserId(),offer.getOfferId(),"OFFER_CANCELLED",null);
            }
        }
        if(r.getJobId()!=null) jobs.lockPaymentJob(r.getJobId()).ifPresent(j -> j.setJobStatus("CANCELLED"));
        event(r,r.getUserId(),null,"BOOKING_"+status,null);
    }
    private void event(BookingRequest r,Long userId,Long offerId,String type,Instant expiry) {
        var e=new BookingDispatchEvent();e.setUserId(userId);e.setRequestId(r.getRequestId());e.setOfferId(offerId);
        e.setEventType(type);e.setCreatedAt(Instant.now());e.setNextDeliveryAt(Instant.now());e.setExpiresAt(expiry);events.save(e);
    }
    private BookingRequest locked(Long id) {return requests.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Booking request not found"));}
    private BookingRequest owned(Long userId,Long id) {var r=locked(id);if(!userId.equals(r.getUserId()))throw new ResourceNotFoundException("Booking request not found");return r;}
    private Long providerId(Long userId) {return providers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Provider not found")).getServiceProviderId();}
    private View view(BookingRequest r) {
        var active=offers.findAllByRequestIdOrderByOfferIdAsc(r.getRequestId()).stream()
                .filter(o -> Set.of("PENDING","ACCEPTED").contains(o.getStatus())).findFirst().orElse(null);
        return new View(r.getRequestId(),r.getStatus(),r.getSelectionMode(),active==null?null:active.getOfferId(),
                active==null?null:active.getProviderId(),active==null?null:active.getExpiresAt(),r.getConfirmationExpiresAt(),
                r.getJobId(),r.getPaymentStatus(),r.getQuotedAmount(),r.getCurrencyCode(),
                r.getPlatformFeePercentage(),r.getPlatformFee(),r.getProviderEarnings(),
                r.getSettlementStatus(),r.getSettlementOperationId(),r.getPaymentFundingMode());
    }
    private BigDecimal zero(BigDecimal value){return value==null?new BigDecimal("0.00"):value;}
    private BigDecimal net(BigDecimal gross,BigDecimal fee,BigDecimal saved){return saved==null?gross.subtract(zero(fee)):saved;}
    private String write(Object value) {try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Cannot encode booking request",e);}}
    private <T>T read(String json,Class<T> type) {try{return mapper.readValue(json,type);}catch(Exception e){throw new IllegalStateException("Cannot read booking request",e);}}
    private List<Candidate> readCandidates(String json) {try{return mapper.readValue(json,new TypeReference<List<Candidate>>(){});}catch(Exception e){throw new IllegalStateException("Cannot read candidates",e);}}
    private String sha256(String value) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
