package com.jobhub.service.customer;

import com.jobhub.entity.job.Job;
import com.jobhub.entity.market.Market;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceTimesTest {
    private static Optional<Market> colombo(Long id) {
        var market = new Market();
        market.setMarketId(id);
        market.setTimezone("Asia/Colombo");
        return Optional.of(market);
    }

    @Test
    void marketLocalTimesBecomeUtcInstants() {
        var job = new Job();
        job.setMarketId(1L);
        job.setActualStartDatetime(LocalDateTime.of(2026, 11, 19, 10, 15, 2));
        job.setActualEndDatetime(LocalDateTime.of(2026, 11, 19, 12, 45, 30));
        job.setCompletionConfirmedAt(Instant.parse("2026-11-19T07:20:00Z"));

        var times = ServiceTimes.of(job, ServiceTimesTest::colombo);

        assertThat(times.actualStartedAt()).isEqualTo(Instant.parse("2026-11-19T04:45:02Z"));
        assertThat(times.actualFinishedAt()).isEqualTo(Instant.parse("2026-11-19T07:15:30Z"));
        assertThat(times.completionConfirmedAt()).isEqualTo(Instant.parse("2026-11-19T07:20:00Z"));
    }

    @Test
    void everythingIsNullBeforeTheServiceStartsWithoutLookingUpTheMarket() {
        var job = new Job();
        job.setMarketId(1L);
        var times = ServiceTimes.of(job, id -> { throw new AssertionError("market lookup not needed"); });
        assertThat(times).isEqualTo(new ServiceTimes(null, null, null));
        assertThat(ServiceTimes.of(null, ServiceTimesTest::colombo)).isEqualTo(new ServiceTimes(null, null, null));
    }

    @Test
    void startedButNotFinished() {
        var job = new Job();
        job.setMarketId(1L);
        job.setActualStartDatetime(LocalDateTime.of(2026, 11, 19, 10, 0));
        var times = ServiceTimes.of(job, ServiceTimesTest::colombo);
        assertThat(times.actualStartedAt()).isEqualTo(Instant.parse("2026-11-19T04:30:00Z"));
        assertThat(times.actualFinishedAt()).isNull();
        assertThat(times.completionConfirmedAt()).isNull();
    }

    @Test
    void missingMarketFailsInsteadOfGuessingAZone() {
        var job = new Job();
        job.setMarketId(9L);
        job.setActualStartDatetime(LocalDateTime.of(2026, 11, 19, 10, 0));
        assertThatThrownBy(() -> ServiceTimes.of(job, id -> Optional.empty())).isInstanceOf(IllegalStateException.class);
    }
}
