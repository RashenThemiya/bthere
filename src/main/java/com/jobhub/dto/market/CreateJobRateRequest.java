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
        @DecimalMin("0.01") BigDecimal areaRadiusKm,
        @jakarta.validation.constraints.Size(max = 10)
        java.util.List<@jakarta.validation.constraints.NotNull @jakarta.validation.Valid PricingConditionRequest> conditions,
        @DecimalMin("0.00") @DecimalMax("100.00") @jakarta.validation.constraints.Digits(integer=3,fraction=2)
        BigDecimal platformFeePercentage
) {
    public CreateJobRateRequest(String billingType, Integer durationMinutes, BigDecimal rate,
            LocalDateTime effectiveFrom, LocalDateTime effectiveTo, Long optionId,
            BigDecimal baseFare, BigDecimal pricePerKm, BigDecimal minimumFare,
            String geographicalAreaName, BigDecimal areaLatitude, BigDecimal areaLongitude,
            BigDecimal areaRadiusKm) {
        this(billingType, durationMinutes, rate, effectiveFrom, effectiveTo, optionId,
                baseFare, pricePerKm, minimumFare, geographicalAreaName, areaLatitude,
                areaLongitude, areaRadiusKm, null, null);
    }
    public CreateJobRateRequest(String billingType,Integer durationMinutes,BigDecimal rate,
            LocalDateTime effectiveFrom,LocalDateTime effectiveTo,Long optionId,
            BigDecimal baseFare,BigDecimal pricePerKm,BigDecimal minimumFare,String geographicalAreaName,
            BigDecimal areaLatitude,BigDecimal areaLongitude,BigDecimal areaRadiusKm,
            java.util.List<PricingConditionRequest> conditions) {
        this(billingType,durationMinutes,rate,effectiveFrom,effectiveTo,optionId,baseFare,pricePerKm,
                minimumFare,geographicalAreaName,areaLatitude,areaLongitude,areaRadiusKm,conditions,null);
    }
}
