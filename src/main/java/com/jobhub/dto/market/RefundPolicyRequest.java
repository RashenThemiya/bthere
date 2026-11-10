package com.jobhub.dto.market;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RefundPolicyRequest(
        Long offeringId,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal noProviderRefundPercentage,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal providerCancellationRefundPercentage,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal providerNoShowRefundPercentage,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal serviceFailureRefundPercentage,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal customerNoShowRefundPercentage,
        boolean platformFeeRefundable,
        boolean processingFeeRefundable,
        @NotNull Instant effectiveFrom,
        Instant effectiveTo,
        @NotEmpty List<@Valid RefundPolicyTierRequest> tiers,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal retainedProviderSharePercentage,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal penaltyCustomerSharePercentage,
        @Min(0) @Max(1440) Integer providerPenaltyGraceMinutes,
        @Min(1) @Max(100) Integer providerCancellationLimit,
        @Min(1) @Max(365) Integer providerCancellationWindowDays
) {
    public RefundPolicyRequest(Long offeringId, String name, String description,
            BigDecimal noProviderRefundPercentage, BigDecimal providerCancellationRefundPercentage,
            BigDecimal providerNoShowRefundPercentage, BigDecimal serviceFailureRefundPercentage,
            BigDecimal customerNoShowRefundPercentage, boolean platformFeeRefundable, boolean processingFeeRefundable,
            Instant effectiveFrom, Instant effectiveTo, List<RefundPolicyTierRequest> tiers,
            BigDecimal retainedProviderSharePercentage) {
        this(offeringId, name, description, noProviderRefundPercentage, providerCancellationRefundPercentage,
                providerNoShowRefundPercentage, serviceFailureRefundPercentage, customerNoShowRefundPercentage,
                platformFeeRefundable, processingFeeRefundable, effectiveFrom, effectiveTo, tiers,
                retainedProviderSharePercentage, null, null, null, null);
    }

    public RefundPolicyRequest(Long offeringId, String name, String description,
            BigDecimal noProviderRefundPercentage, BigDecimal providerCancellationRefundPercentage,
            BigDecimal providerNoShowRefundPercentage, BigDecimal serviceFailureRefundPercentage,
            BigDecimal customerNoShowRefundPercentage, boolean platformFeeRefundable, boolean processingFeeRefundable,
            Instant effectiveFrom, Instant effectiveTo, List<RefundPolicyTierRequest> tiers) {
        this(offeringId, name, description, noProviderRefundPercentage, providerCancellationRefundPercentage,
                providerNoShowRefundPercentage, serviceFailureRefundPercentage, customerNoShowRefundPercentage,
                platformFeeRefundable, processingFeeRefundable, effectiveFrom, effectiveTo, tiers, null, null, null, null, null);
    }
}
