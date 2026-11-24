package com.jobhub.config;

import com.jobhub.service.customer.AutoCancelRules;
import com.jobhub.service.customer.BookingAutoCancelService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Cancels paid bookings the provider never started, once their auto-cancel time has passed. */
@Configuration
@RequiredArgsConstructor
public class BookingAutoCancelWorker {
    private static final Logger log = LoggerFactory.getLogger(BookingAutoCancelWorker.class);
    /** A booking whose cancel failed (for example a card refund that needs reconciliation) is retried after this. */
    static final Duration RETRY_AFTER = Duration.ofMinutes(15);

    private final BookingAutoCancelService service;
    private final AutoCancelRules rules;
    private final Map<Long, Instant> retryAt = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${booking.auto-cancel.scan-ms:60000}", initialDelayString = "${booking.auto-cancel.initial-delay-ms:30000}")
    public void scan() {
        if (!rules.enabled()) return;
        Instant now = Instant.now();
        retryAt.values().removeIf(at -> !now.isBefore(at));
        for (Long jobId : service.candidates()) {
            if (retryAt.containsKey(jobId)) continue;
            try {
                if (service.cancelIfDue(jobId)) log.info("Booking {} auto-cancelled: not started in time", jobId);
            } catch (RuntimeException e) {
                retryAt.put(jobId, now.plus(RETRY_AFTER));
                log.warn("Booking {} auto-cancel deferred: {} {}", jobId, e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
