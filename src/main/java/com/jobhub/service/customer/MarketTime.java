package com.jobhub.service.customer;

import com.jobhub.entity.job.Job;
import com.jobhub.entity.market.Market;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A booking's market and its scheduled times as UTC instants, for API responses. Booking start/end times are
 * stored as market-local date-times; clients need the zone (or the instant) to show countdowns correctly on a
 * phone set to another zone. Computed on read, so old bookings get the same fields. Fields are null when the
 * booking has no market or the market has no valid time zone.
 */
public record MarketTime(Long marketId, String marketTimezone, Instant startUtc, Instant endUtc) {
    static final MarketTime NONE = new MarketTime(null, null, null, null);

    public static MarketTime of(Long marketId, LocalDateTime start, LocalDateTime end,
                                Function<Long, Optional<Market>> markets) {
        if (marketId == null) return NONE;
        ZoneId zone = markets.apply(marketId).map(Market::getTimezone).map(MarketTime::zone).orElse(null);
        if (zone == null) return new MarketTime(marketId, null, null, null);
        return new MarketTime(marketId, zone.getId(), instant(start, zone), instant(end, zone));
    }

    public static MarketTime of(Job job, Function<Long, Optional<Market>> markets) {
        return job == null ? NONE : of(job.getMarketId(), job.getStartDatetime(), job.getExpectedEndDatetime(), markets);
    }

    /** Wraps a market lookup so a list response loads each market once. */
    public static Function<Long, Optional<Market>> cached(Function<Long, Optional<Market>> markets) {
        Map<Long, Optional<Market>> seen = new HashMap<>();
        return id -> seen.computeIfAbsent(id, markets);
    }

    private static ZoneId zone(String id) {
        try { return id == null || id.isBlank() ? null : ZoneId.of(id); } catch (DateTimeException e) { return null; }
    }

    private static Instant instant(LocalDateTime value, ZoneId zone) {
        return value == null ? null : value.atZone(zone).toInstant();
    }
}
