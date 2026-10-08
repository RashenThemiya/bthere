package com.jobhub.dto.market;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Complete replacement of the editable fields of an existing job rate.
 * Omitted JSON properties are deserialized as null and therefore have the same
 * meaning as an explicitly supplied null value.
 */
public record UpdateJobRateRequest(
        @NotBlank String billingType,
        Integer durationMinutes,
        @DecimalMin("0.0") BigDecimal rate,
        @NotNull(message = "effectiveFrom is required") LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Long optionId,
        @DecimalMin("0.0") BigDecimal baseFare,
        @DecimalMin("0.0") BigDecimal pricePerKm,
        @DecimalMin("0.0") BigDecimal minimumFare,
        String geographicalAreaName,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal areaLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal areaLongitude,
        @DecimalMin("0.01") BigDecimal areaRadiusKm
) {
}
