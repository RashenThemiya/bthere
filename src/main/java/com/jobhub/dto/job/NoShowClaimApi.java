package com.jobhub.dto.job;

import com.jobhub.dto.job.CancellationApi.Penalty;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/** Customer no-show complaints: provider files, customer may respond, admin approves or rejects. */
public final class NoShowClaimApi {
    private NoShowClaimApi() {}

    /**
     * Whether the provider may file now. reason is null when eligible, otherwise one of NOT_TIME_BASED,
     * NOT_ENABLED, ALREADY_SUBMITTED, NOT_WAITING_TO_START, NOT_PAID, TOO_EARLY, TOO_LATE.
     * opensAt/closesAt are market-local, like other booking times.
     */
    public record Eligibility(Long jobId, boolean eligible, String reason, LocalDateTime opensAt, LocalDateTime closesAt,
                              Integer maxDistanceMeters, Integer photoMaxAgeMinutes) {}

    public record Submit(
            @NotBlank @Size(max = 300) String photoKey,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @PositiveOrZero @Max(100000) Integer accuracyMeters,
            @NotNull Instant deviceCapturedAt,
            @PositiveOrZero @Max(100) Integer callAttempts,
            @Size(max = 2000) String note) {}

    public record CustomerResponse(@NotBlank @Size(max = 2000) String note) {}

    /** Overrides are optional; without them the booking's refund policy decides. */
    public record Approve(
            @NotBlank @Size(max = 2000) String reason,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal refundPercentage,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal providerSharePercentage) {}

    /** penaltyAmount is optional (0 or missing means no penalty); at most the booking amount. */
    public record Reject(
            @NotBlank @Size(max = 2000) String reason,
            @DecimalMin("0.00") BigDecimal penaltyAmount) {}

    /**
     * The complaint. For the customer the evidence location and photo fields are null.
     * photoUrl is a 10-minute signed link, filled only in the admin detail view.
     */
    public record Claim(Long claimId, Long jobId, Long requestId, Long serviceProviderId, String status,
                        Instant submittedAt, Long minutesAfterStart, Integer callAttempts, String providerNote,
                        String photoKey, String photoUrl, Instant photoUploadedAt, BigDecimal latitude, BigDecimal longitude,
                        Integer accuracyMeters, Integer distanceMeters, Boolean distanceFlagged, Instant deviceCapturedAt,
                        String customerNote, Instant customerRespondedAt,
                        Instant decidedAt, String decisionReason, String currencyCode, BigDecimal refundPercentage,
                        BigDecimal refundAmount, BigDecimal providerAmount, BigDecimal platformAmount,
                        BigDecimal penaltyAmount, Penalty penalty) {}

    /** Per service option. noShowClaimAfterMinutes null = complaints off for the option. */
    public record Settings(
            Long optionId, String schedulingModel, boolean enabled,
            @Min(1) @Max(1440) Integer noShowClaimAfterMinutes,
            @Min(1) @Max(720) Integer noShowClaimDeadlineHours,
            @Min(10) @Max(100000) Integer noShowMaxDistanceMeters,
            @Min(1) @Max(240) Integer noShowPhotoMaxAgeMinutes) {}
}
