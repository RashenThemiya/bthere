package com.jobhub.service.customer;

import com.jobhub.dto.job.CancellationApi.AutoCancelSettings;
import com.jobhub.dto.job.CancellationApi.AutoCancelUpdate;
import com.jobhub.dto.job.CancellationApi.Cancel;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.job.BookingRequestRepository;
import com.jobhub.repository.job.JobCancellationRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.RefundPolicyRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cancels paid bookings the provider never started (see {@link AutoCancelRules}). The customer is refunded in full to
 * their wallet with no cancellation fee; both sides get a booking event and push. Runs from the server's scheduler,
 * so it does not depend on either app being open.
 */
@Service
@RequiredArgsConstructor
public class BookingAutoCancelService {
    static final int SCAN_LIMIT = 200;

    private final BookingRequestRepository requests;
    private final JobRepository jobs;
    private final JobCancellationRepository cancellations;
    private final ServiceProviderRepository providers;
    private final ServiceOptionRepository options;
    private final MarketRepository markets;
    private final RefundPolicyRepository policies;
    private final AutoCancelRules rules;
    private final BookingRequestService bookingRequests;
    private final BookingCancellationService bookingCancellations;
    private final AuditService audit;

    /** Bookings past their start in every market; the rules decide which are due. */
    @Transactional(readOnly = true)
    public List<Long> candidates() {
        var ids = new ArrayList<Long>();
        for (var market : markets.findAll()) {
            ZoneId zone;
            try { zone = ZoneId.of(market.getTimezone().trim()); } catch (RuntimeException e) { continue; }
            ids.addAll(jobs.findAutoCancelCandidates(market.getMarketId(), LocalDateTime.ofInstant(rules.appliesFrom(), zone),
                    LocalDateTime.now(zone), PageRequest.of(0, SCAN_LIMIT)));
        }
        return ids;
    }

    /**
     * Cancels the booking if it is due now. Locks the booking request, then the job: the same order as START-code
     * verification, so a start and an auto-cancel arriving together are serialised and the second one sees the
     * first one's result (a started booking is never cancelled). Returns true when it cancelled.
     */
    @Transactional
    public boolean cancelIfDue(Long jobId) {
        var request = requests.lockByJobId(jobId).orElse(null);
        if (request == null) return false;
        var job = jobs.lockPaymentJob(jobId).orElse(null);
        var plan = rules.plan(job, request);
        if (plan == null || Instant.now().isBefore(plan.autoCancelAt())) return false;
        if (cancellations.findByJobId(jobId).isPresent()) return false;

        Long policyId = request.getRefundPolicyId() != null ? request.getRefundPolicyId() : bookingRequests.currentRefundPolicyId(job);
        var policy = policyId == null ? null : policies.findById(policyId).orElse(null);
        var zone = ZoneId.of(markets.findById(job.getMarketId()).orElseThrow().getTimezone().trim());
        long minutesBeforeStart = Duration.between(Instant.now(), job.getStartDatetime().atZone(zone).toInstant()).toMinutes();
        var quote = RefundCalculator.autoCancelled(policy, job, minutesBeforeStart);
        Long providerUserId = job.getAssignedServiceProviderId() == null ? null
                : providers.findById(job.getAssignedServiceProviderId()).map(p -> p.getUserId()).orElse(null);

        bookingRequests.autoCancelConfirmed(request, providerUserId);
        String note = AutoCancelRules.REASON_NOT_STARTED.equals(plan.reason())
                ? "Not started within " + plan.graceMinutes() + " minutes of the booked start"
                : "Not started before the service window closed (" + plan.hold() + ")";
        var row = bookingCancellations.settle(request, job, quote, null, new Cancel(plan.reason(), note, null));
        audit.record(null, "BOOKING_AUTO_CANCELLED", "JOB", jobId, null,
                Map.of("reason", plan.reason(), "hold", plan.hold() == null ? "NONE" : plan.hold(),
                        "autoCancelAt", plan.autoCancelAt().toString(), "refundAmount", quote.refundAmount(),
                        "cancellationId", row.getCancellationId()));
        return true;
    }

    // ---------- settings (per service option) ----------

    @Transactional(readOnly = true)
    public AutoCancelSettings settings(Long optionId) {
        return settingsView(optionId, options.findById(optionId)
                .orElseThrow(() -> new ResourceNotFoundException("Service option not found")).getAutoCancelAfterMinutes());
    }

    /** autoCancelAfterMinutes null goes back to the server default. Applies to existing bookings too. */
    @Transactional
    public AutoCancelSettings updateSettings(Long adminId, Long optionId, AutoCancelUpdate in) {
        var o = options.findById(optionId).orElseThrow(() -> new ResourceNotFoundException("Service option not found"));
        var before = settingsView(optionId, o.getAutoCancelAfterMinutes());
        o.setAutoCancelAfterMinutes(in.autoCancelAfterMinutes());
        options.save(o);
        var after = settingsView(optionId, o.getAutoCancelAfterMinutes());
        audit.record(adminId, "AUTO_CANCEL_SETTINGS_UPDATED", "SERVICE_OPTION", optionId, before, after);
        return after;
    }

    private AutoCancelSettings settingsView(Long optionId, Integer minutes) {
        return new AutoCancelSettings(optionId, rules.enabled(), minutes, rules.defaultGraceMinutes(),
                minutes == null ? rules.defaultGraceMinutes() : minutes);
    }
}
