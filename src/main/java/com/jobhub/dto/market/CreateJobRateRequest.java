package com.jobhub.dto.market;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateJobRateRequest(
        @NotBlank String billingType,
        Integer durationMinutes,
        @DecimalMin("0.0") BigDecimal rate,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Long optionId,
        @DecimalMin("0.0") BigDecimal baseFare,
        @DecimalMin("0.0") BigDecimal pricePerKm,
        @DecimalMin("0.0") BigDecimal minimumFare,
        String geographicalAreaName,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal areaLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal areaLongitude,
        @DecimalMin("0.01") BigDecimal areaRadiusKm
) {}
