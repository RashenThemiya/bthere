package com.jobhub.dto.market;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Null removes an override; an explicit zero disables commission at this scope. */
public record PlatformFeeRequest(
        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer=3,fraction=2)
        BigDecimal platformFeePercentage) {}
