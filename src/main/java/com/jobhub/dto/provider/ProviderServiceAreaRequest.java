package com.jobhub.dto.provider;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record ProviderServiceAreaRequest(
        @NotBlank @Size(max = 150) String locationName,
        @Size(max = 100) String country,
        @Size(max = 100) String province,
        @Size(max = 100) String district,
        @Size(max = 100) String city,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @DecimalMin(value = "0.1") @DecimalMax("500.0") BigDecimal radiusKm,
        @Min(1) Integer capacity
) {}
