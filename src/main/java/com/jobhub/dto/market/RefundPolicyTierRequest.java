package com.jobhub.dto.market;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record RefundPolicyTierRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @PositiveOrZero Integer minimumMinutesBeforeStart,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal refundPercentage,
        @NotNull @DecimalMin("0.00") BigDecimal cancellationFee,
        @NotNull @Positive Integer priority
) {
}
