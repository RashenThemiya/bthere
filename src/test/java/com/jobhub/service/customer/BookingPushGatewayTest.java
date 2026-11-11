package com.jobhub.service.customer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingPushGatewayTest {
    @Test void cancellationsAndOutcomesAreVisibleButRevocationsStaySilent() {
        assertThat(BookingPushGateway.VISIBLE).containsKeys("BOOKING_CANCELLED", "BOOKING_CANCELLED_BY_CUSTOMER",
                "BOOKING_UNMATCHED", "BOOKING_EXPIRED", "BOOKING_STARTED", "PROVIDER_ACCEPTED", "BOOKING_OFFER");
        assertThat(BookingPushGateway.VISIBLE).doesNotContainKeys("OFFER_CANCELLED", "OFFER_EXPIRED", "BOOKING_CODE_REPLACED");
        BookingPushGateway.VISIBLE.values().forEach(text -> {
            assertThat(text).hasSize(2);
            assertThat(text[0]).isNotBlank();
            assertThat(text[1]).isNotBlank();
        });
    }
}
