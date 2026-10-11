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
        @NotEmpty List<@Valid RefundPolicyTierRequest> tiers
) {
}
