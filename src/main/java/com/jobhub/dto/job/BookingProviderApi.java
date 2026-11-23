package com.jobhub.dto.job;

import java.math.BigDecimal;
import java.util.List;

/** Customer Booking Details: who is (or was) assigned to one of the customer's bookings. */
public final class BookingProviderApi {
    private BookingProviderApi() {}

    /**
     * Whether the customer may see and call this provider's phone right now.
     * phoneNumber is null whenever callAllowed is false; unavailableReason is null whenever it is true.
     */
    public record Contact(boolean callAllowed, String phoneNumber, String unavailableReason) {}

    /**
     * role is PRIMARY for the job's assigned provider and TEAM_MEMBER for other active team providers.
     * ratedByYou is true when this customer has already rated this provider for this booking.
     */
    public record BookingProvider(Long providerId, String role, String displayName, String profilePhoto,
                                  BigDecimal averageRating, Integer totalRatings, boolean ratedByYou,
                                  Contact contact) {}

    /** contactAllowedStatuses lists the jobStatus values in which a phone can be shown at all. */
    public record BookingProviders(Long jobId, String jobStatus, String fulfillmentModel,
                                   List<String> contactAllowedStatuses, List<BookingProvider> providers,
                                   java.time.Instant actualStartedAt, java.time.Instant actualFinishedAt,
                                   java.time.Instant completionConfirmedAt,
                                   /* startDatetime/expectedEndDatetime: market-local booked times;
                                      *Utc: the same as UTC instants; marketTimezone: IANA zone. */
                                   Long marketId, String marketTimezone,
                                   java.time.LocalDateTime startDatetime, java.time.LocalDateTime expectedEndDatetime,
                                   java.time.Instant startDatetimeUtc, java.time.Instant expectedEndDatetimeUtc) {}
}
