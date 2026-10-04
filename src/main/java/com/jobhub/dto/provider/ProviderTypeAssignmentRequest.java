package com.jobhub.dto.provider;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProviderTypeAssignmentRequest(
        @NotNull(message = "Provider type ID is required")
        Long providerTypeId,

        @NotNull(message = "Experience years is required")
        @DecimalMin(value = "0.0", message = "Experience years cannot be negative")
        @DecimalMax(value = "100.0", message = "Experience years cannot exceed 100")
        BigDecimal experienceYears
) {
}
