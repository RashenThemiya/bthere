package com.jobhub.service.customer;

import com.jobhub.dto.job.CancellationApi.CancellationSummary;
import com.jobhub.entity.job.BookingRequest;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.JobCancellation;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.repository.job.BookingRequestRepository;
import com.jobhub.repository.job.JobCancellationRepository;
import com.jobhub.repository.job.JobProviderTrackingRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;

/**
 * When a paid booking that the provider never started is cancelled automatically. Read-only: the cancellation itself
 * is done by {@link BookingAutoCancelService}.
 *
 * <ul>
 *   <li>Bookings that start before booking.auto-cancel.applies-to-starts-from are never auto-cancelled.</li>
 *   <li>Only CONFIRMED, paid (CARD PAID or WALLET RESERVED) bookings with no actual start are auto-cancelled.
 *       NO_SHOW_REVIEW, IN_PROGRESS and every later state never are.</li>
 *   <li>Normally the booking is cancelled at start + grace (the option's autoCancelAfterMinutes, else the default).</li>
 *   <li>If the provider showed up (tracking ARRIVED) or tried a start code, the booking is held instead: it is
 *       cancelled only when the start-code window and the no-show complaint window have both closed, so the provider
 *       can still start or file a no-show complaint.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AutoCancelRules {
    public static final String SYSTEM = "SYSTEM";
    /** Cancelled at start + grace: the provider did not start, arrive or try a code. */
    public static final String REASON_NOT_STARTED = "PROVIDER_DID_NOT_START";
    /** Held booking (provider arrived or tried a code) still not started when the service window closed. */
    public static final String REASON_WINDOW_CLOSED = "NOT_STARTED_IN_SERVICE_WINDOW";
    public static final String HOLD_ARRIVED = "PROVIDER_ARRIVED", HOLD_CODE_TRIED = "START_CODE_TRIED";
    static final int CODE_WINDOW_HOURS = 24;

    private final BookingRequestRepository requests;
    private final JobCancellationRepository cancellations;
    private final JobProviderTrackingRepository trackings;
    private final ServiceOptionRepository options;
    private final MarketRepository markets;

    @Value("${booking.auto-cancel.enabled:true}")
    private boolean enabled = true;
    @Value("${booking.auto-cancel.default-grace-minutes:60}")
    private int defaultGraceMinutes = 60;
    /** Bookings booked to start before this instant are left alone (they predate the feature). */
    @Value("${booking.auto-cancel.applies-to-starts-from:2026-11-24T00:00:00Z}")
    private Instant appliesFrom = Instant.parse("2026-11-24T00:00:00Z");

    /** autoCancelAt is UTC; hold is null, PROVIDER_ARRIVED or START_CODE_TRIED. */
    public record Plan(Instant autoCancelAt, String reason, String hold, int graceMinutes) {}

    /** What the booking responses show: the pending auto-cancel, or how the booking was cancelled. */
    public record Info(Instant autoCancelAt, String autoCancelHold, CancellationSummary cancellation) {
        static final Info NONE = new Info(null, null, null);
    }

    public boolean enabled() { return enabled; }

    public int defaultGraceMinutes() { return defaultGraceMinutes; }

    public Instant appliesFrom() { return appliesFrom; }

    public int graceMinutes(ServiceOption option) {
        return option == null || option.getAutoCancelAfterMinutes() == null ? defaultGraceMinutes : option.getAutoCancelAfterMinutes();
    }

    /** Null when this booking is not waiting to be auto-cancelled. */
    public Plan plan(Job job, BookingRequest request) {
        if (!enabled || job == null || request == null || job.getStartDatetime() == null || job.getActualStartDatetime() != null
                || !"CONFIRMED".equals(job.getJobStatus()) || !"CONFIRMED".equals(request.getStatus()) || !paid(job))
            return null;
        ZoneId zone = zone(job.getMarketId());
        if (zone == null) return null;
        var option = job.getServiceOptionId() == null ? null : options.findById(job.getServiceOptionId()).orElse(null);
        int grace = graceMinutes(option);
        Instant start = job.getStartDatetime().atZone(zone).toInstant();
        if (start.isBefore(appliesFrom)) return null;
        Instant normal = start.plus(Duration.ofMinutes(grace));
        String hold = trackings.findAllByJobId(job.getJobId()).stream().anyMatch(t -> t.getArrivedAt() != null) ? HOLD_ARRIVED
                : request.getStartAttempts() > 0 ? HOLD_CODE_TRIED : null;
        if (hold == null) return new Plan(normal, REASON_NOT_STARTED, null, grace);
        LocalDateTime end = job.getExpectedEndDatetime() != null ? job.getExpectedEndDatetime() : job.getStartDatetime();
        Instant codeCloses = request.getCodeExpiresAt() != null ? request.getCodeExpiresAt()
                : end.plusHours(CODE_WINDOW_HOURS).atZone(zone).toInstant();
        Instant held = latest(normal, codeCloses, noShowCloses(job, option, end, zone));
        return new Plan(held, REASON_WINDOW_CLOSED, hold, grace);
    }

    /** For booking responses. Looks the request or cancellation up only when the status needs it. */
    public Info info(Job job) {
        if (job == null) return Info.NONE;
        if ("CONFIRMED".equals(job.getJobStatus())) {
            var plan = plan(job, requests.findByJobId(job.getJobId()).orElse(null));
            return plan == null ? Info.NONE : new Info(plan.autoCancelAt(), plan.hold(), null);
        }
        if ("CANCELLED".equals(job.getJobStatus()))
            return cancellations.findByJobId(job.getJobId()).map(c -> new Info(null, null, summary(c))).orElse(Info.NONE);
        return Info.NONE;
    }

    /** True when the booking was cancelled by this flow (start-code errors use it to explain the 409). */
    public boolean autoCancelled(Long jobId) {
        return jobId != null && cancellations.findByJobId(jobId).map(c -> SYSTEM.equals(c.getCancelledBy())).orElse(false);
    }

    static CancellationSummary summary(JobCancellation c) {
        Instant at = c.getCreatedAt() == null ? null : c.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant();
        return new CancellationSummary(c.getCancelledBy(), c.getReason(), SYSTEM.equals(c.getCancelledBy()),
                c.getCurrencyCode(), c.getPaidAmount(), c.getRefundAmount(), c.getCancellationFee(),
                c.getRefundDestination(), at);
    }

    /** Same window as NoShowClaimService.check: max(end, start + claimAfter) + deadline hours; null when off. */
    private static Instant noShowCloses(Job job, ServiceOption option, LocalDateTime end, ZoneId zone) {
        if (option == null || option.getNoShowClaimAfterMinutes() == null) return null;
        String model = job.getSchedulingModel() != null ? job.getSchedulingModel()
                : option.getSchedulingModel() == null ? "TIME_BASED" : option.getSchedulingModel();
        if (!"TIME_BASED".equals(model)) return null;
        LocalDateTime opens = job.getStartDatetime().plusMinutes(option.getNoShowClaimAfterMinutes());
        int hours = option.getNoShowClaimDeadlineHours() == null ? NoShowClaimService.DEFAULT_DEADLINE_HOURS
                : option.getNoShowClaimDeadlineHours();
        return (end.isAfter(opens) ? end : opens).plusHours(hours).atZone(zone).toInstant();
    }

    private ZoneId zone(Long marketId) {
        if (marketId == null) return null;
        var tz = markets.findById(marketId).map(m -> m.getTimezone()).orElse(null);
        if (tz == null || tz.isBlank()) return null;
        try { return ZoneId.of(tz.trim()); } catch (DateTimeException e) { return null; }
    }

    static boolean paid(Job job) {
        return "CARD".equals(job.getPaymentMethod()) && "PAID".equals(job.getPaymentStatus())
                || "WALLET".equals(job.getPaymentMethod()) && "RESERVED".equals(job.getPaymentStatus());
    }

    private static Instant latest(Instant a, Instant b, Instant c) {
        Instant m = a.isAfter(b) ? a : b;
        return c != null && c.isAfter(m) ? c : m;
    }
}
