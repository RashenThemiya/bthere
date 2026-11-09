package com.jobhub.dto.job;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class BookingNoticeApi {
    private BookingNoticeApi() {}

    /** minimumNoticeMinutes 0 to 43200; 0 allows "now" bookings. instantBooking is read-only (ignored on PUT). */
    public record Settings(@NotNull @Min(0) @Max(43200) Integer minimumNoticeMinutes, boolean instantBooking) {}
}
