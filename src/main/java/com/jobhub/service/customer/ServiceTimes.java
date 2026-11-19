package com.jobhub.service.customer;

import com.jobhub.entity.job.Job;
import com.jobhub.entity.market.Market;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Function;

/**
 * When a booking's service actually ran, as UTC instants for API responses.
 * actualStartedAt: the provider verified the START code. actualFinishedAt: the provider finished (requested
 * completion). completionConfirmedAt: the customer confirmed completion. Each is null until it happens.
 * The first two are stored as market-local times, so they are converted with the booking market's time zone.
 */
public record ServiceTimes(Instant actualStartedAt, Instant actualFinishedAt, Instant completionConfirmedAt) {
    static final ServiceTimes NONE = new ServiceTimes(null, null, null);

    public static ServiceTimes of(Job job, Function<Long, Optional<Market>> markets) {
        if (job == null) return NONE;
        LocalDateTime started = job.getActualStartDatetime();
        LocalDateTime finished = job.getActualEndDatetime();
        if (started == null && finished == null) return new ServiceTimes(null, null, job.getCompletionConfirmedAt());
        ZoneId zone = markets.apply(job.getMarketId())
                .map(m -> ZoneId.of(m.getTimezone()))
                .orElseThrow(() -> new IllegalStateException("Booking market not found"));
        return new ServiceTimes(instant(started, zone), instant(finished, zone), job.getCompletionConfirmedAt());
    }

    private static Instant instant(LocalDateTime value, ZoneId zone) {
        return value == null ? null : value.atZone(zone).toInstant();
    }
}
