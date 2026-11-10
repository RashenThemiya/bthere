package com.jobhub.service.customer;

import com.jobhub.dto.job.CancellationApi.*;
import com.jobhub.entity.job.BookingRequest;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.JobCancellation;
import com.jobhub.entity.market.RefundPolicy;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.job.BookingRequestRepository;
import com.jobhub.repository.job.JobCancellationRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.RefundPolicyRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.service.audit.AuditService;
import com.jobhub.service.finance.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Cancelling a paid booking request before the service starts (status CONFIRMED). Refunds always go to the
 * customer's wallet. Customer cancellations follow the refund policy captured when the booking was confirmed;
 * provider cancellations refund the customer in full.
 */
@Service
@RequiredArgsConstructor
public class BookingCancellationService {
    private final BookingRequestRepository requests;
    private final JobRepository jobs;
    private final ServiceProviderRepository providers;
    private final MarketRepository markets;
    private final RefundPolicyRepository policies;
    private final JobCancellationRepository cancellations;
    private final WalletService wallets;
    private final BookingRequestService bookingRequests;
    private final AuditService audit;

    // ---------- customer ----------

    @Transactional(readOnly = true)
    public Quote customerPreview(Long userId, Long jobId) {
        var request = customerRequest(userId, jobId);
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        requireCancellable(request, job);
        return RefundCalculator.customer(requirePolicy(request, job), job, minutesBeforeStart(job));
    }

    @Transactional
    public Cancellation cancelByCustomer(Long userId, Long jobId, Cancel input) {
        var request = requests.lockByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!userId.equals(request.getUserId())) throw new ResourceNotFoundException("Booking not found");
        var existing = cancellations.findByJobId(jobId);
        if (existing.isPresent()) return view(existing.get(), jobs.findById(jobId).orElseThrow());
        var job = jobs.lockPaymentJob(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        requireCancellable(request, job);
        var quote = RefundCalculator.customer(requirePolicy(request, job), job, minutesBeforeStart(job));
        requireExpected(input, quote);
        Long providerUserId = providerUserId(job);
        bookingRequests.cancelConfirmed(request, providerUserId, "BOOKING_CANCELLED_BY_CUSTOMER");
        return settle(request, job, quote, userId, input);
    }

    @Transactional(readOnly = true)
    public Cancellation customerCancellation(Long userId, Long jobId) {
        var request = customerRequest(userId, jobId);
        return found(request.getJobId());
    }

    // ---------- provider ----------

    @Transactional
    public Cancellation cancelByProvider(Long userId, Long jobId, Cancel input) {
        var request = requests.lockByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        var job = jobs.lockPaymentJob(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!providerId(userId).equals(job.getAssignedServiceProviderId())) throw new ResourceNotFoundException("Booking not found");
        var existing = cancellations.findByJobId(jobId);
        if (existing.isPresent()) return view(existing.get(), job);
        requireCancellable(request, job);
        var quote = RefundCalculator.provider(policy(request, job), job, minutesBeforeStart(job));
        bookingRequests.cancelConfirmed(request, null, null);
        var result = settle(request, job, quote, userId, input);
        audit.record(userId, "PROVIDER_CANCELLED_PAID_BOOKING", "JOB", jobId, null, result);
        return result;
    }

    @Transactional(readOnly = true)
    public Cancellation providerCancellation(Long userId, Long jobId) {
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!providerId(userId).equals(job.getAssignedServiceProviderId())) throw new ResourceNotFoundException("Booking not found");
        return found(jobId);
    }

    // ---------- admin ----------

    @Transactional(readOnly = true)
    public Cancellation adminCancellation(Long jobId) {
        return found(jobId);
    }

    // ---------- internals ----------

    private Cancellation settle(BookingRequest request, Job job, Quote quote, Long actorUserId, Cancel input) {
        var operation = wallets.refundCancelledRequestBooking(request.getUserId(), job.getJobId(), quote.refundAmount(),
                quote.providerAmount(), quote.platformAmount(), actorUserId);
        var row = new JobCancellation();
        row.setJobId(job.getJobId()); row.setRequestId(request.getRequestId());
        row.setCancelledByUserId(actorUserId); row.setCancelledBy(quote.cancelledBy());
        row.setReason(input.reason().trim()); row.setNote(input.note() == null || input.note().isBlank() ? null : input.note().trim());
        row.setCurrencyCode(quote.currencyCode()); row.setPaidAmount(quote.paidAmount());
        row.setRefundAmount(quote.refundAmount()); row.setRefundPercentage(quote.refundPercentage());
        row.setCancellationFee(quote.cancellationFee()); row.setProviderAmount(quote.providerAmount());
        row.setPlatformAmount(quote.platformAmount()); row.setRefundDestination(quote.refundDestination());
        row.setMinutesBeforeStart(quote.minutesBeforeStart()); row.setRefundPolicyId(quote.refundPolicyId());
        row.setRefundPolicyVersion(quote.refundPolicyVersion()); row.setRefundTierName(quote.refundTierName());
        row.setWalletOperationId(operation.operationId());
        row = cancellations.saveAndFlush(row);
        audit.record(actorUserId, "BOOKING_CANCELLED", "JOB", job.getJobId(), null,
                java.util.Map.of("cancelledBy", quote.cancelledBy(), "refundAmount", quote.refundAmount()));
        return view(row, job);
    }

    private void requireCancellable(BookingRequest request, Job job) {
        if (!"CONFIRMED".equals(request.getStatus()) || !"CONFIRMED".equals(job.getJobStatus()))
            throw new ConflictException("Only a paid booking that has not started can be cancelled");
        boolean paid = "CARD".equals(job.getPaymentMethod()) && "PAID".equals(job.getPaymentStatus())
                || "WALLET".equals(job.getPaymentMethod()) && "RESERVED".equals(job.getPaymentStatus());
        if (!paid) throw new ConflictException("Only a paid booking that has not started can be cancelled");
    }

    private void requireExpected(Cancel input, Quote quote) {
        if (input.expectedRefundAmount() != null && input.expectedRefundAmount().compareTo(quote.refundAmount()) != 0)
            throw new ConflictException("The refund amount changed to " + quote.refundAmount() + "; review it and try again");
    }

    private RefundPolicy requirePolicy(BookingRequest request, Job job) {
        var policy = policy(request, job);
        if (policy == null) throw new ConflictException("No refund policy is configured for this service; contact support to cancel");
        return policy;
    }

    /** The policy captured at confirmation; bookings confirmed before snapshots existed use today's policy. */
    private RefundPolicy policy(BookingRequest request, Job job) {
        Long id = request.getRefundPolicyId() != null ? request.getRefundPolicyId() : bookingRequests.currentRefundPolicyId(job);
        return id == null ? null : policies.findById(id).orElse(null);
    }

    private long minutesBeforeStart(Job job) {
        var zone = ZoneId.of(markets.findById(job.getMarketId()).orElseThrow().getTimezone());
        return Duration.between(Instant.now(), job.getStartDatetime().atZone(zone).toInstant()).toMinutes();
    }

    private BookingRequest customerRequest(Long userId, Long jobId) {
        var request = requests.findByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!userId.equals(request.getUserId())) throw new ResourceNotFoundException("Booking not found");
        return request;
    }

    private Long providerUserId(Job job) {
        return job.getAssignedServiceProviderId() == null ? null
                : providers.findById(job.getAssignedServiceProviderId()).map(p -> p.getUserId()).orElse(null);
    }

    private Long providerId(Long userId) {
        return providers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Provider not found"))
                .getServiceProviderId();
    }

    private Cancellation found(Long jobId) {
        var row = cancellations.findByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Cancellation not found"));
        return view(row, jobs.findById(jobId).orElse(null));
    }

    private static Cancellation view(JobCancellation c, Job job) {
        return new Cancellation(c.getCancellationId(), c.getJobId(), c.getRequestId(), c.getCancelledBy(),
                c.getCancelledByUserId(), c.getReason(), c.getNote(), c.getCurrencyCode(), c.getPaidAmount(),
                c.getRefundAmount(), c.getRefundPercentage(), c.getCancellationFee(), c.getProviderAmount(),
                c.getPlatformAmount(), c.getRefundDestination(), c.getMinutesBeforeStart(), c.getRefundPolicyId(),
                c.getRefundPolicyVersion(), c.getRefundTierName(), c.getWalletOperationId(),
                job == null ? null : job.getPaymentStatus(), c.getCreatedAt());
    }
}
