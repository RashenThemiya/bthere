package com.jobhub.service.customer;

import com.jobhub.dto.job.CancellationApi.Cancel;
import com.jobhub.dto.job.CancellationApi.Quote;
import com.jobhub.dto.job.NoShowClaimApi.*;
import com.jobhub.entity.job.BookingRequest;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.NoShowClaim;
import com.jobhub.entity.job.ProviderPenalty;
import com.jobhub.entity.market.RefundPolicy;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.job.*;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.RefundPolicyRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.service.audit.AuditService;
import com.jobhub.service.finance.WalletService;
import com.jobhub.service.upload.S3PrivateDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Customer no-show complaints on paid, time-based bookings that never started. The provider files after the
 * option's waiting period with a fresh photo and GPS position; the booking then waits in NO_SHOW_REVIEW with the
 * money held and no provider penalty. An admin approves (customer charged per the refund policy, provider paid a
 * share to their wallet) or rejects (customer refunded in full, optional provider penalty).
 */
@Service
@RequiredArgsConstructor
public class NoShowClaimService {
    public static final String REVIEW_STATUS = "NO_SHOW_REVIEW";
    static final int DEFAULT_DEADLINE_HOURS = 24, DEFAULT_MAX_DISTANCE_METERS = 500, DEFAULT_PHOTO_MAX_AGE_MINUTES = 15;
    static final String PENDING = "PENDING", APPROVED = "APPROVED", REJECTED = "REJECTED";

    private final NoShowClaimRepository claims;
    private final BookingRequestRepository requests;
    private final JobRepository jobs;
    private final ServiceProviderRepository providers;
    private final ServiceOptionRepository options;
    private final MarketRepository markets;
    private final RefundPolicyRepository policies;
    private final JobCancellationRepository cancellations;
    private final ProviderPenaltyRepository penalties;
    private final BookingRequestService bookingRequests;
    private final BookingCancellationService bookingCancellations;
    private final WalletService wallets;
    private final S3PrivateDocumentService documents;
    private final AuditService audit;

    // ---------- provider ----------

    @Transactional(readOnly = true)
    public Eligibility eligibility(Long userId, Long jobId) {
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        requireAssigned(provider(userId), job);
        var request = requests.findByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        return check(job, request, option(job), Instant.now());
    }

    @Transactional
    public Claim submit(Long userId, Long jobId, Submit in) {
        var request = requests.lockByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        var job = jobs.lockPaymentJob(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        var provider = provider(userId);
        requireAssigned(provider, job);
        var existing = claims.findByJobId(jobId);
        if (existing.isPresent()) return view(existing.get(), false, null);

        var option = option(job);
        Instant now = Instant.now();
        var eligibility = check(job, request, option, now);
        if (!eligibility.eligible()) throw new ConflictException(message(eligibility));

        ZoneId zone = zone(job);
        Instant start = job.getStartDatetime().atZone(zone).toInstant();
        Instant uploaded = documents.providerPhotoUploadedAt(userId, in.photoKey().trim());
        int maxAge = orDefault(option.getNoShowPhotoMaxAgeMinutes(), DEFAULT_PHOTO_MAX_AGE_MINUTES);
        // The photo must be taken at the booking time: uploaded after the booked start and only minutes ago.
        if (uploaded == null || uploaded.isBefore(start) || uploaded.isBefore(now.minus(Duration.ofMinutes(maxAge))))
            throw new IllegalArgumentException("Take a new photo now; it must be uploaded within the last " + maxAge + " minutes");

        int maxDistance = orDefault(option.getNoShowMaxDistanceMeters(), DEFAULT_MAX_DISTANCE_METERS);
        Integer distance = job.getLatitude() == null || job.getLongitude() == null ? null
                : (int) Math.round(distanceMeters(job.getLatitude(), job.getLongitude(), in.latitude(), in.longitude()));

        var claim = new NoShowClaim();
        claim.setJobId(jobId); claim.setRequestId(request.getRequestId());
        claim.setServiceProviderId(provider.getServiceProviderId()); claim.setProviderUserId(userId);
        claim.setCustomerUserId(request.getUserId()); claim.setStatus(PENDING);
        claim.setPhotoKey(in.photoKey().trim()); claim.setPhotoUploadedAt(uploaded);
        claim.setLatitude(in.latitude().setScale(7, RoundingMode.HALF_UP)); claim.setLongitude(in.longitude().setScale(7, RoundingMode.HALF_UP));
        claim.setAccuracyMeters(in.accuracyMeters()); claim.setDistanceMeters(distance);
        claim.setDistanceFlagged(distance != null && distance > maxDistance);
        claim.setDeviceCapturedAt(in.deviceCapturedAt()); claim.setSubmittedAt(now);
        claim.setMinutesAfterStart(Duration.between(start, now).toMinutes());
        claim.setCallAttempts(in.callAttempts()); claim.setProviderNote(blankToNull(in.note()));
        claim.setCurrencyCode(job.getCurrencyCode());
        claim = claims.saveAndFlush(claim);

        bookingRequests.markNoShowReview(request, job);
        bookingRequests.notifyUser(request, request.getUserId(), "NO_SHOW_REPORTED");
        var result = view(claim, false, null);
        audit.record(userId, "NO_SHOW_CLAIM_SUBMITTED", "JOB", jobId, null,
                Map.of("claimId", claim.getClaimId(), "distanceMeters", distance == null ? "unknown" : distance,
                        "distanceFlagged", claim.isDistanceFlagged()));
        return result;
    }

    @Transactional(readOnly = true)
    public Claim providerClaim(Long userId, Long jobId) {
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        requireAssigned(provider(userId), job);
        return view(found(jobId), false, null);
    }

    // ---------- customer ----------

    @Transactional(readOnly = true)
    public Claim customerClaim(Long userId, Long jobId) {
        customerRequest(userId, jobId);
        return view(found(jobId), true, null);
    }

    @Transactional
    public Claim respond(Long userId, Long jobId, CustomerResponse in) {
        var request = customerRequest(userId, jobId);
        var claim = found(jobId);
        claim = claims.lockById(claim.getClaimId()).orElseThrow();
        if (!PENDING.equals(claim.getStatus())) throw new ConflictException("This complaint has already been decided");
        if (claim.getCustomerNote() != null) throw new ConflictException("You have already responded to this complaint");
        claim.setCustomerNote(in.note().trim());
        claim.setCustomerRespondedAt(Instant.now());
        claims.save(claim);
        bookingRequests.notifyUser(request, claim.getProviderUserId(), "NO_SHOW_CUSTOMER_RESPONDED");
        audit.record(userId, "NO_SHOW_CLAIM_CUSTOMER_RESPONDED", "NO_SHOW_CLAIM", claim.getClaimId(), null, null);
        return view(claim, true, null);
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public Page<Claim> list(String status, Long providerId, Pageable pageable) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !Set.of(PENDING, APPROVED, REJECTED).contains(normalized))
            throw new IllegalArgumentException("Status must be PENDING, APPROVED or REJECTED");
        return claims.search(normalized, providerId, pageable).map(c -> view(c, false, null));
    }

    @Transactional(readOnly = true)
    public Claim adminClaim(Long claimId) {
        var claim = claims.findById(claimId).orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));
        return view(claim, false, documents.adminViewUrl(claim.getPhotoKey()).url());
    }

    @Transactional
    public Claim approve(Long adminId, Long claimId, Approve in) {
        var c = lockPending(claimId);
        var request = requests.lockByJobId(c.getJobId()).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        var job = jobs.lockPaymentJob(c.getJobId()).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        requireInReview(request, job);
        var policy = policy(request, job);
        BigDecimal refundPct = in.refundPercentage() != null ? in.refundPercentage()
                : policy == null ? null : policy.getCustomerNoShowRefundPercentage();
        if (refundPct == null) throw new ConflictException(
                "Set customerNoShowRefundPercentage on the refund policy, or send refundPercentage");
        BigDecimal sharePct = in.providerSharePercentage() != null ? in.providerSharePercentage()
                : policy == null ? null : policy.getRetainedProviderSharePercentage();
        var quote = RefundCalculator.customerNoShow(policy, job, refundPct, sharePct, minutesBeforeStart(job));

        bookingRequests.cancelConfirmed(request, null, null);
        var row = bookingCancellations.settle(request, job, quote, adminId,
                new Cancel("CUSTOMER_NO_SHOW", in.reason().trim(), null));
        decide(c, adminId, APPROVED, in.reason(), quote, row.getCancellationId());
        claims.save(c);
        bookingRequests.notifyUser(request, c.getProviderUserId(), "NO_SHOW_CLAIM_APPROVED");
        bookingRequests.notifyUser(request, c.getCustomerUserId(), "NO_SHOW_RESOLVED");
        var result = view(c, false, null);
        audit.record(adminId, "NO_SHOW_CLAIM_APPROVED", "NO_SHOW_CLAIM", claimId, null,
                Map.of("refundAmount", quote.refundAmount(), "providerAmount", quote.providerAmount(),
                        "platformAmount", quote.platformAmount()));
        return result;
    }

    @Transactional
    public Claim reject(Long adminId, Long claimId, Reject in) {
        var c = lockPending(claimId);
        var request = requests.lockByJobId(c.getJobId()).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        var job = jobs.lockPaymentJob(c.getJobId()).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        requireInReview(request, job);
        var policy = policy(request, job);
        var quote = RefundCalculator.noShowRejected(policy, job, minutesBeforeStart(job));
        BigDecimal penalty = in.penaltyAmount() == null ? BigDecimal.ZERO.setScale(2) : in.penaltyAmount().setScale(2, RoundingMode.DOWN);
        if (penalty.compareTo(quote.paidAmount()) > 0)
            throw new IllegalArgumentException("penaltyAmount cannot be more than the booking amount " + quote.paidAmount());

        bookingRequests.cancelConfirmed(request, null, null);
        var row = bookingCancellations.settle(request, job, quote, adminId,
                new Cancel("NO_SHOW_CLAIM_REJECTED", in.reason().trim(), null));
        decide(c, adminId, REJECTED, in.reason(), quote, row.getCancellationId());
        if (penalty.signum() > 0) {
            // Counted like a provider cancellation (suspension limit); waiving the penalty removes it from the count.
            row.setCancelledBy(RefundCalculator.PROVIDER);
            row.setCancelledByUserId(c.getProviderUserId());
            var p = new ProviderPenalty();
            p.setJobId(job.getJobId()); p.setCancellationId(row.getCancellationId());
            p.setServiceProviderId(c.getServiceProviderId()); p.setProviderUserId(c.getProviderUserId());
            p.setCustomerUserId(c.getCustomerUserId()); p.setCurrencyCode(job.getCurrencyCode());
            p.setPenaltyAmount(penalty);
            p.setCustomerShareAmount(policy == null || policy.getPenaltyCustomerSharePercentage() == null ? BigDecimal.ZERO.setScale(2)
                    : penalty.multiply(policy.getPenaltyCustomerSharePercentage()).divide(new BigDecimal("100"), 2, RoundingMode.DOWN).min(penalty));
            p.setCollectedAmount(BigDecimal.ZERO.setScale(2)); p.setCustomerPaidAmount(BigDecimal.ZERO.setScale(2));
            p.setTierName("No-show complaint rejected");
            p.setRefundPolicyId(quote.refundPolicyId()); p.setRefundPolicyVersion(quote.refundPolicyVersion());
            p.setStatus("OUTSTANDING");
            p = penalties.saveAndFlush(p);
            row.setPenaltyAmount(penalty); row.setPenaltyId(p.getPenaltyId());
            row = cancellations.saveAndFlush(row);
            wallets.collectProviderPenalty(p.getPenaltyId(), adminId);
            c.setPenaltyAmount(penalty); c.setPenaltyId(p.getPenaltyId());
            var provider = providers.findById(c.getServiceProviderId()).orElse(null);
            if (provider != null && bookingCancellations.suspendIfOverLimit(provider, policy, c.getProviderUserId())) {
                row.setProviderSuspended(true);
                cancellations.saveAndFlush(row);
            }
        }
        claims.save(c);
        bookingRequests.notifyUser(request, c.getProviderUserId(), "NO_SHOW_CLAIM_REJECTED");
        bookingRequests.notifyUser(request, c.getCustomerUserId(), "NO_SHOW_RESOLVED");
        var result = view(c, false, null);
        audit.record(adminId, "NO_SHOW_CLAIM_REJECTED", "NO_SHOW_CLAIM", claimId, null,
                Map.of("refundAmount", quote.refundAmount(), "penaltyAmount", penalty));
        return result;
    }

    // ---------- settings (per service option) ----------

    @Transactional(readOnly = true)
    public Settings settings(Long optionId) {
        return settingsView(options.findById(optionId).orElseThrow(() -> new ResourceNotFoundException("Service option not found")));
    }

    @Transactional
    public Settings updateSettings(Long adminId, Long optionId, Settings in) {
        var o = options.findById(optionId).orElseThrow(() -> new ResourceNotFoundException("Service option not found"));
        if (in.noShowClaimAfterMinutes() != null && !"TIME_BASED".equals(model(o)))
            throw new IllegalArgumentException("No-show complaints are only available for TIME_BASED service options");
        var before = settingsView(o);
        o.setNoShowClaimAfterMinutes(in.noShowClaimAfterMinutes());
        o.setNoShowClaimDeadlineHours(in.noShowClaimDeadlineHours());
        o.setNoShowMaxDistanceMeters(in.noShowMaxDistanceMeters());
        o.setNoShowPhotoMaxAgeMinutes(in.noShowPhotoMaxAgeMinutes());
        options.save(o);
        var after = settingsView(o);
        audit.record(adminId, "NO_SHOW_SETTINGS_UPDATED", "SERVICE_OPTION", optionId, before, after);
        return after;
    }

    // ---------- rules ----------

    Eligibility check(Job job, BookingRequest request, ServiceOption option, Instant now) {
        int deadlineHours = orDefault(option.getNoShowClaimDeadlineHours(), DEFAULT_DEADLINE_HOURS);
        Integer after = option.getNoShowClaimAfterMinutes();
        LocalDateTime opens = after == null || job.getStartDatetime() == null ? null : job.getStartDatetime().plusMinutes(after);
        LocalDateTime end = job.getExpectedEndDatetime() != null ? job.getExpectedEndDatetime() : job.getStartDatetime();
        LocalDateTime closes = opens == null || end == null ? null : max(end, opens).plusHours(deadlineHours);
        String reason = null;
        String model = job.getSchedulingModel() != null ? job.getSchedulingModel() : model(option);
        if (!"TIME_BASED".equals(model)) reason = "NOT_TIME_BASED";
        else if (opens == null) reason = "NOT_ENABLED";
        else if (claims.findByJobId(job.getJobId()).isPresent()) reason = "ALREADY_SUBMITTED";
        else if (!"CONFIRMED".equals(request.getStatus()) || !"CONFIRMED".equals(job.getJobStatus())) reason = "NOT_WAITING_TO_START";
        else if (!paid(job)) reason = "NOT_PAID";
        else {
            ZoneId zone = zone(job);
            if (now.isBefore(opens.atZone(zone).toInstant())) reason = "TOO_EARLY";
            else if (now.isAfter(closes.atZone(zone).toInstant())) reason = "TOO_LATE";
        }
        return new Eligibility(job.getJobId(), reason == null, reason, opens, closes,
                orDefault(option.getNoShowMaxDistanceMeters(), DEFAULT_MAX_DISTANCE_METERS),
                orDefault(option.getNoShowPhotoMaxAgeMinutes(), DEFAULT_PHOTO_MAX_AGE_MINUTES));
    }

    private static String message(Eligibility e) {
        return switch (e.reason()) {
            case "NOT_TIME_BASED" -> "No-show complaints are only available for time-based bookings";
            case "NOT_ENABLED" -> "No-show complaints are not enabled for this service";
            case "NOT_WAITING_TO_START" -> "A complaint can only be filed for a confirmed booking that has not started";
            case "NOT_PAID" -> "A complaint can only be filed for a paid booking";
            case "TOO_EARLY" -> "A complaint can be filed from " + e.opensAt();
            case "TOO_LATE" -> "The time to file a complaint ended at " + e.closesAt();
            default -> "A complaint cannot be filed for this booking";
        };
    }

    private static boolean paid(Job job) {
        return "CARD".equals(job.getPaymentMethod()) && "PAID".equals(job.getPaymentStatus())
                || "WALLET".equals(job.getPaymentMethod()) && "RESERVED".equals(job.getPaymentStatus());
    }

    /** Great-circle distance in metres. */
    static double distanceMeters(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double p1 = Math.toRadians(lat1.doubleValue()), p2 = Math.toRadians(lat2.doubleValue());
        double dp = p2 - p1, dl = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void decide(NoShowClaim c, Long adminId, String status, String reason, Quote quote, Long cancellationId) {
        c.setStatus(status); c.setDecidedByUserId(adminId); c.setDecidedAt(Instant.now()); c.setDecisionReason(reason.trim());
        c.setCurrencyCode(quote.currencyCode()); c.setRefundPercentage(quote.refundPercentage());
        c.setRefundAmount(quote.refundAmount()); c.setProviderAmount(quote.providerAmount());
        c.setPlatformAmount(quote.platformAmount()); c.setCancellationId(cancellationId);
    }

    private NoShowClaim lockPending(Long claimId) {
        var c = claims.lockById(claimId).orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));
        if (!PENDING.equals(c.getStatus())) throw new ConflictException("This complaint has already been " + c.getStatus().toLowerCase(Locale.ROOT));
        return c;
    }

    private static void requireInReview(BookingRequest request, Job job) {
        if (!REVIEW_STATUS.equals(request.getStatus()) || !REVIEW_STATUS.equals(job.getJobStatus()))
            throw new ConflictException("The booking is no longer waiting for this complaint's review");
    }

    private RefundPolicy policy(BookingRequest request, Job job) {
        Long id = request.getRefundPolicyId() != null ? request.getRefundPolicyId() : bookingRequests.currentRefundPolicyId(job);
        return id == null ? null : policies.findById(id).orElse(null);
    }

    private long minutesBeforeStart(Job job) {
        return Duration.between(Instant.now(), job.getStartDatetime().atZone(zone(job)).toInstant()).toMinutes();
    }

    private ZoneId zone(Job job) {
        return ZoneId.of(markets.findById(job.getMarketId()).orElseThrow().getTimezone());
    }

    private ServiceOption option(Job job) {
        return options.findById(job.getServiceOptionId()).orElseThrow(() -> new ResourceNotFoundException("Service option not found"));
    }

    private static String model(ServiceOption o) { return o.getSchedulingModel() == null ? "TIME_BASED" : o.getSchedulingModel(); }

    private Settings settingsView(ServiceOption o) {
        return new Settings(o.getOptionId(), model(o), o.getNoShowClaimAfterMinutes() != null && "TIME_BASED".equals(model(o)),
                o.getNoShowClaimAfterMinutes(), o.getNoShowClaimDeadlineHours(), o.getNoShowMaxDistanceMeters(),
                o.getNoShowPhotoMaxAgeMinutes());
    }

    private ServiceProvider provider(Long userId) {
        return providers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
    }

    private static void requireAssigned(ServiceProvider provider, Job job) {
        if (!provider.getServiceProviderId().equals(job.getAssignedServiceProviderId()))
            throw new ResourceNotFoundException("Booking not found");
    }

    private BookingRequest customerRequest(Long userId, Long jobId) {
        var request = requests.findByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!userId.equals(request.getUserId())) throw new ResourceNotFoundException("Booking not found");
        return request;
    }

    private NoShowClaim found(Long jobId) {
        return claims.findByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Complaint not found"));
    }

    /** customerView hides the provider's exact position and photo. */
    private Claim view(NoShowClaim c, boolean customerView, String photoUrl) {
        var penalty = c.getPenaltyId() == null ? null
                : penalties.findById(c.getPenaltyId()).map(BookingCancellationService::penaltyView).orElse(null);
        return new Claim(c.getClaimId(), c.getJobId(), c.getRequestId(), c.getServiceProviderId(), c.getStatus(),
                c.getSubmittedAt(), c.getMinutesAfterStart(), c.getCallAttempts(), c.getProviderNote(),
                customerView ? null : c.getPhotoKey(), photoUrl, customerView ? null : c.getPhotoUploadedAt(),
                customerView ? null : c.getLatitude(), customerView ? null : c.getLongitude(),
                customerView ? null : c.getAccuracyMeters(), customerView ? null : c.getDistanceMeters(),
                customerView ? null : c.isDistanceFlagged(), customerView ? null : c.getDeviceCapturedAt(),
                c.getCustomerNote(), c.getCustomerRespondedAt(), c.getDecidedAt(), c.getDecisionReason(),
                c.getCurrencyCode(), c.getRefundPercentage(), c.getRefundAmount(), c.getProviderAmount(),
                c.getPlatformAmount(), c.getPenaltyAmount(), customerView ? null : penalty);
    }

    private static int orDefault(Integer value, int fallback) { return value == null ? fallback : value; }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) { return a.isAfter(b) ? a : b; }

    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
