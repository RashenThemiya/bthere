package com.jobhub.service.customer;

import com.jobhub.entity.job.Job;
import com.jobhub.entity.market.Market;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MarketTimeTest {
    private static Optional<Market> market(Long id, String zone) {
        var market = new Market();
        market.setMarketId(id);
        market.setTimezone(zone);
        return Optional.of(market);
    }

    @Test
    void bookedLocalTimesComeWithTheMarketZoneAndUtcInstants() {
        var job = new Job();
        job.setMarketId(1L);
        job.setStartDatetime(LocalDateTime.of(2026, 11, 25, 10, 0));
        job.setExpectedEndDatetime(LocalDateTime.of(2026, 11, 25, 12, 0));

        var time = MarketTime.of(job, id -> market(id, "Asia/Colombo"));

        assertThat(time).isEqualTo(new MarketTime(1L, "Asia/Colombo",
                Instant.parse("2026-11-25T04:30:00Z"), Instant.parse("2026-11-25T06:30:00Z")));
    }

    @Test
    void missingTimesStayNullButTheMarketIsStillReturned() {
        var time = MarketTime.of(1L, LocalDateTime.of(2026, 11, 25, 10, 0), null, id -> market(id, "Asia/Colombo"));
        assertThat(time.startUtc()).isEqualTo(Instant.parse("2026-11-25T04:30:00Z"));
        assertThat(time.endUtc()).isNull();
    }

    @Test
    void anUnknownMarketOrBadZoneNeverBreaksTheResponse() {
        var start = LocalDateTime.of(2026, 11, 25, 10, 0);
        assertThat(MarketTime.of(null, start, null, id -> { throw new AssertionError("no lookup without a market"); }))
                .isEqualTo(new MarketTime(null, null, null, null));
        assertThat(MarketTime.of(7L, start, null, id -> Optional.empty())).isEqualTo(new MarketTime(7L, null, null, null));
        assertThat(MarketTime.of(7L, start, null, id -> market(id, "Not/AZone"))).isEqualTo(new MarketTime(7L, null, null, null));
        assertThat(MarketTime.of((Job) null, id -> Optional.empty())).isEqualTo(new MarketTime(null, null, null, null));
    }

    @Test
    void cachedLookupLoadsEachMarketOnce() {
        var calls = new AtomicInteger();
        var lookup = MarketTime.cached(id -> { calls.incrementAndGet(); return market(id, "Asia/Colombo"); });
        for (int i = 0; i < 5; i++) MarketTime.of(1L, LocalDateTime.of(2026, 11, 25, 10, i), null, lookup);
        MarketTime.of(2L, LocalDateTime.of(2026, 11, 25, 10, 0), null, lookup);
        assertThat(calls).hasValue(2);
    }
}
