package com.jobhub.service.admin;

import com.jobhub.dto.admin.BookingMonitorApi.*;
import com.jobhub.entity.job.*;
import com.jobhub.entity.finance.*;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.job.*;
import com.jobhub.repository.finance.*;
import com.jobhub.service.customer.CustomerBookingService;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class BookingMonitorService {
    private final BookingRequestRepository requests;
    private final JobRepository jobs;
    private final BookingOfferRepository offers;
    private final BookingPaymentNoticeRepository payments;
    private final WalletOperationRepository transactions;
    private final WalletLedgerRepository ledger;
    private final BookingDispatchEventRepository events;
    private final CustomerBookingService bookingDetails;

    public Summary summary() {
        return new Summary(counts(requests.monitorStatusCounts()),counts(jobs.monitorStatusCounts()),
            counts(requests.monitorPaymentCounts()),counts(requests.monitorSettlementCounts()));
    }
    private Map<String,Long> counts(List<Object[]> groups) {
        var map=new TreeMap<String,Long>();
        for(var row:groups)map.put(row[0]==null?"UNRECORDED":row[0].toString(),((Number)row[1]).longValue());
        return map;
    }
    public Page<RequestRow> requests(RequestFilter f,int page,int size) {
        Specification<BookingRequest> spec=BookingMonitorService.<BookingRequest>eq("requestId",f.requestId());
        spec=spec.and(BookingMonitorService.<BookingRequest>eq("jobId",f.jobId())).and(BookingMonitorService.<BookingRequest>eq("userId",f.customerUserId()))
            .and(BookingMonitorService.<BookingRequest>eq("marketId",f.marketId())).and(BookingMonitorService.<BookingRequest>eq("optionId",f.optionId())).and(BookingMonitorService.<BookingRequest>eq("status",norm(f.status())))
            .and(BookingMonitorService.<BookingRequest>eq("paymentStatus",norm(f.paymentStatus()))).and(BookingMonitorService.<BookingRequest>eq("settlementStatus",norm(f.settlementStatus())))
            .and(BookingMonitorService.<BookingRequest>eq("paymentFundingMode",norm(f.fundingMode()))).and(BookingMonitorService.<BookingRequest,java.time.LocalDateTime>range("createdAt",f.createdFrom(),f.createdTo()));
        if(f.providerId()!=null)spec=spec.and((r,q,c) -> {
            var sub=q.subquery(Long.class);var o=sub.from(BookingOffer.class);
            sub.select(o.get("requestId")).where(c.equal(o.get("requestId"),r.get("requestId")),c.equal(o.get("providerId"),f.providerId()));
            return c.exists(sub);
        });
        return requests.findAll(spec,paging(page,size,"requestId")).map(this::requestRow);
    }
    public RequestDetail request(Long id) {
        var r=requireRequest(id);var job=r.getJobId()==null?null:jobs.findById(r.getJobId()).orElse(null);
        return new RequestDetail(requestRow(r),job==null?null:bookingRow(job,r.getRequestId()));
    }
    public Page<BookingRow> bookings(BookingFilter f,int page,int size) {
        Specification<Job> spec=BookingMonitorService.<Job>eq("jobId",f.jobId());
        spec=spec.and(BookingMonitorService.<Job>eq("customerId",f.customerId())).and(BookingMonitorService.<Job>eq("marketId",f.marketId()))
            .and(BookingMonitorService.<Job>eq("serviceProviderTypeId",f.serviceTypeId())).and(BookingMonitorService.<Job>eq("serviceOptionId",f.optionId()))
            .and(BookingMonitorService.<Job>eq("jobStatus",norm(f.status()))).and(BookingMonitorService.<Job>eq("paymentStatus",norm(f.paymentStatus())))
            .and(BookingMonitorService.<Job>eq("paymentMethod",norm(f.paymentMethod()))).and(BookingMonitorService.<Job>eq("schedulingModel",norm(f.schedulingModel())))
            .and(BookingMonitorService.<Job,java.time.LocalDateTime>range("createdAt",f.createdFrom(),f.createdTo()));
        if(f.providerId()!=null)spec=spec.and((r,q,c) -> {
            var sub=q.subquery(Long.class);var a=sub.from(JobProviderAssignment.class);
            sub.select(a.get("jobId")).where(c.equal(a.get("jobId"),r.get("jobId")),c.equal(a.get("serviceProviderId"),f.providerId()));
            return c.or(c.equal(r.get("assignedServiceProviderId"),f.providerId()),c.exists(sub));
        });
        var result=jobs.findAll(spec,paging(page,size,"jobId"));
        Map<Long,Long> requestIds=new HashMap<>();
        if(!result.isEmpty())requests.findAllByJobIdIn(result.getContent().stream().map(Job::getJobId).toList())
            .forEach(r -> requestIds.put(r.getJobId(),r.getRequestId()));
        return result.map(j -> bookingRow(j,requestIds.get(j.getJobId())));
    }
    public BookingDetail booking(Long id) {
        var j=jobs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        var r=requests.findByJobId(id).orElse(null);
        return new BookingDetail(bookingRow(j,r==null?null:r.getRequestId()),r==null?null:requestRow(r),bookingDetails.dispatchResponse(j));
    }
    public Page<OfferRow> offers(OfferFilter f,int page,int size) {
        Specification<BookingOffer> spec=BookingMonitorService.<BookingOffer>eq("requestId",f.requestId());
        return offers.findAll(spec.and(BookingMonitorService.<BookingOffer>eq("providerId",f.providerId())).and(BookingMonitorService.<BookingOffer>eq("status",norm(f.status())))
            .and(BookingMonitorService.<BookingOffer,java.time.LocalDateTime>range("createdAt",f.createdFrom(),f.createdTo())),paging(page,size,"offerId")).map(o ->
                new OfferRow(o.getOfferId(),o.getRequestId(),o.getProviderId(),o.getProviderLocationId(),o.getStatus(),o.getExpiresAt(),o.getCreatedAt()));
    }
    public Page<PaymentRow> payments(PaymentFilter f,int page,int size) {
        if(f.statusCode()!=null && !Set.of("2","0","-1","-2","-3").contains(f.statusCode()))
            throw new IllegalArgumentException("Unsupported PayHere statusCode");
        Specification<BookingPaymentNotice> spec=BookingMonitorService.<BookingPaymentNotice>eq("requestId",f.requestId());
        spec=spec.and(BookingMonitorService.<BookingPaymentNotice>eq("paymentId",clean(f.paymentId()))).and(BookingMonitorService.<BookingPaymentNotice>eq("statusCode",f.statusCode()))
            .and(BookingMonitorService.<BookingPaymentNotice,java.time.Instant>range("receivedAt",f.receivedFrom(),f.receivedTo()));
        if(norm(f.fundingMode())!=null)spec=spec.and((r,q,c) -> {
            var sub=q.subquery(Long.class);var br=sub.from(BookingRequest.class);
            sub.select(br.get("requestId")).where(c.equal(br.get("requestId"),r.get("requestId")),
                c.equal(br.get("paymentFundingMode"),norm(f.fundingMode())));return c.exists(sub);
        });
        var result=payments.findAll(spec,paging(page,size,"noticeId"));
        Map<Long,BookingRequest> parents=result.isEmpty()?Map.of():requests.findAllById(result.getContent().stream()
            .map(BookingPaymentNotice::getRequestId).distinct().toList()).stream().collect(Collectors.toMap(BookingRequest::getRequestId,Function.identity()));
        return result.map(n -> paymentRow(n,parents.get(n.getRequestId())));
    }
    public Page<TransactionRow> transactions(TransactionFilter f,int page,int size) {
        Specification<WalletOperation> spec=BookingMonitorService.<WalletOperation>eq("jobId",f.jobId());
        return transactions.findAll(spec.and(BookingMonitorService.<WalletOperation>eq("ownerUserId",f.ownerUserId())).and(BookingMonitorService.<WalletOperation>eq("type",norm(f.type())))
            .and(BookingMonitorService.<WalletOperation>eq("status",norm(f.status()))).and(BookingMonitorService.<WalletOperation>eq("fundingMode",norm(f.fundingMode())))
            .and(BookingMonitorService.<WalletOperation>eq("paymentReference",clean(f.paymentReference()))).and(BookingMonitorService.<WalletOperation,java.time.LocalDateTime>range("createdAt",f.createdFrom(),f.createdTo())),
            paging(page,size,"operationId")).map(this::transactionRow);
    }
    public TransactionRow transaction(Long id) {return transactionRow(requireTransaction(id));}
    public Page<LedgerRow> entries(Long id,int page,int size) {
        requireTransaction(id);
        return ledger.findAllByOperationId(id,paging(page,size,"walletLedgerEntryId")).map(e -> new LedgerRow(
            e.getWalletLedgerEntryId(),e.getOperationId(),e.getWalletId(),e.getAccountKey(),e.getBucket(),e.getCurrencyCode(),
            e.getDelta(),e.getBalanceBefore(),e.getBalanceAfter(),e.getCreatedAt()));
    }
    public Page<EventRow> events(EventFilter f,int page,int size) {
        Specification<BookingDispatchEvent> spec=BookingMonitorService.<BookingDispatchEvent>eq("requestId",f.requestId());
        return events.findAll(spec.and(BookingMonitorService.<BookingDispatchEvent>eq("userId",f.userId())).and(BookingMonitorService.<BookingDispatchEvent>eq("eventType",norm(f.eventType())))
            .and(BookingMonitorService.<BookingDispatchEvent>eq("deliveryStatus",norm(f.deliveryStatus()))).and(BookingMonitorService.<BookingDispatchEvent,java.time.Instant>range("createdAt",f.createdFrom(),f.createdTo())),
            paging(page,size,"eventId")).map(e -> new EventRow(e.getEventId(),e.getRequestId(),e.getOfferId(),e.getUserId(),
                e.getEventType(),e.getCreatedAt(),e.getExpiresAt(),e.getDeliveryStatus(),e.getDeliveryAttempts(),e.getNextDeliveryAt()));
    }
    private BookingRequest requireRequest(Long id) {return requests.findById(id).orElseThrow(() -> new ResourceNotFoundException("Booking request not found"));}
    private WalletOperation requireTransaction(Long id) {return transactions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Wallet transaction not found"));}
    private RequestRow requestRow(BookingRequest r) {return new RequestRow(r.getRequestId(),r.getUserId(),r.getMarketId(),r.getOptionId(),
        r.getSelectionMode(),r.getStatus(),r.getJobId(),r.getPaymentStatus(),r.getQuotedAmount(),r.getCurrencyCode(),
        r.getPlatformFeePercentage(),r.getPlatformFee(),r.getProviderEarnings(),r.getPaymentFundingMode(),r.getPaymentOrderId(),
        r.getGatewayPaymentId(),r.getSettlementStatus(),r.getSettlementOperationId(),r.getExpiresAt(),r.getConfirmationExpiresAt(),
        r.getPaymentStartedAt(),r.getCreatedAt(),r.getUpdatedAt());}
    private BookingRow bookingRow(Job j,Long requestId) {return new BookingRow(j.getJobId(),requestId,j.getCustomerId(),
        j.getAssignedServiceProviderId(),j.getMarketId(),j.getServiceProviderTypeId(),j.getServiceOptionId(),j.getJobStatus(),
        j.getPaymentMethod(),j.getPaymentStatus(),j.getSchedulingModel(),j.getDeliveryMode(),j.getFulfillmentModel(),j.getCurrencyCode(),
        j.getExpectedAmount(),j.getTotalAmount(),j.getPlatformFeePercentage(),j.getPlatformFee(),j.getProviderEarnings(),j.getStartDatetime(),
        j.getExpectedEndDatetime(),j.getActualStartDatetime(),j.getActualEndDatetime(),j.getCreatedAt(),j.getUpdatedAt());}
    private PaymentRow paymentRow(BookingPaymentNotice n,BookingRequest r) {return new PaymentRow(n.getNoticeId(),n.getRequestId(),
        n.getPaymentId(),n.getStatusCode(),switch(n.getStatusCode()) {case "2" -> "SUCCESS";case "0" -> "PENDING";
            case "-1" -> "CANCELLED";case "-2" -> "FAILED";case "-3" -> "CHARGEDBACK";default -> "UNKNOWN";},n.getReceivedAt(),
        r!=null && Objects.equals(n.getPaymentId(),r.getGatewayPaymentId()),r==null?null:r.getPaymentOrderId(),r==null?null:r.getPaymentStatus(),
        r==null?null:r.getCurrencyCode(),r==null?null:r.getQuotedAmount(),r==null?null:r.getPaymentFundingMode(),r==null?null:r.getJobId(),
        r==null?null:r.getSettlementStatus(),r==null?null:r.getSettlementOperationId());}
    private TransactionRow transactionRow(WalletOperation o) {return new TransactionRow(o.getOperationId(),o.getType(),o.getOwnerUserId(),
        o.getActorUserId(),o.getJobId(),o.getCurrencyCode(),o.getAmount(),o.getStatus(),o.getPlatformFeePercentage(),o.getPlatformFee(),
        o.getProviderEarnings(),o.getFundingMode(),o.getPaymentReference(),o.getCreatedAt());}
    private static String clean(String s) {return s==null||s.isBlank()?null:s.trim();}
    private static String norm(String s) {return clean(s)==null?null:clean(s).toUpperCase(Locale.ROOT);}
    private static Pageable paging(int page,int size,String id) {
        if(page<0 || size<1 || size>100)throw new IllegalArgumentException("page must be nonnegative and size must be 1 to 100");
        return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,id));
    }
    private static <T> Specification<T> eq(String field,Object value) {return (r,q,c) -> value==null?c.conjunction():c.equal(r.get(field),value);}
    private static <T,V extends Comparable<? super V>> Specification<T> range(String field,V from,V to) {
        if(from!=null && to!=null && from.compareTo(to)>=0)throw new IllegalArgumentException("Date range must have from before to");
        return (r,q,c) -> {var p=new ArrayList<Predicate>();
            if(from!=null)p.add(c.greaterThanOrEqualTo(r.<V>get(field),from));
            if(to!=null)p.add(c.lessThan(r.<V>get(field),to));return c.and(p.toArray(Predicate[]::new));};
    }
}
