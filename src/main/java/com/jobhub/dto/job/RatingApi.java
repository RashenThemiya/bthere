package com.jobhub.dto.job;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class RatingApi {
    private RatingApi() {}

    /** providerId may be omitted when the booking has exactly one provider. */
    public record Rate(@Positive Long providerId,
                       @NotNull @Min(1) @Max(5) Integer rating,
                       @Size(max = 1000) String comment) {}

    public record RatingView(Long ratingId, Long jobId, Long providerId, Long serviceTypeId, Long optionId,
                             int rating, String comment, String status, LocalDateTime createdAt) {}

    /** One line per provider on the booking, so the app knows whom it still has to rate. */
    public record JobProviderRating(Long providerId, String displayName, String profilePhoto, boolean rated,
                                    RatingView rating) {}

    /** canRate is true when the booking is COMPLETED and at least one provider is still unrated. */
    public record JobRatings(Long jobId, Long optionId, String jobStatus, boolean canRate,
                             List<JobProviderRating> providers) {}

    /** distribution keys are "1".."5"; values are counts of visible ratings. */
    public record Stats(BigDecimal averageRating, int totalRatings, Map<String, Integer> distribution) {}

    public record OptionStats(Long optionId, String optionName, BigDecimal averageRating, int totalRatings,
                              Map<String, Integer> distribution) {}

    public record ProviderSummary(Long providerId, String displayName, Stats overall, List<OptionStats> options) {}

    /** customerName is shortened ("Nimali S.") so reviews never expose a full name. */
    public record Review(Long ratingId, Long optionId, String optionName, int rating, String comment,
                         String customerName, LocalDateTime createdAt) {}

    public record AdminRating(Long ratingId, Long jobId, Long providerId, String providerName, Long customerId,
                              String customerName, Long serviceTypeId, Long optionId, int rating, String comment,
                              String status, String moderationReason, Long moderatedBy, LocalDateTime createdAt,
                              LocalDateTime updatedAt) {}

    public record Moderate(@NotBlank @Pattern(regexp = "VISIBLE|HIDDEN") String status,
                           @Size(max = 500) String reason) {}
}
