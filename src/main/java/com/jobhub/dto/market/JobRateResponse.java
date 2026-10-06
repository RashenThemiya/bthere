package com.jobhub.dto.market;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record JobRateResponse(
        Long rateId, Long marketId, Long serviceTypeId, Long optionId, String currencyCode,
        String billingType, Integer durationMinutes, BigDecimal rate,
        BigDecimal baseFare, BigDecimal pricePerKm, BigDecimal minimumFare,
        String geographicalAreaName, BigDecimal areaLatitude,
        BigDecimal areaLongitude, BigDecimal areaRadiusKm,
        LocalDateTime effectiveFrom, LocalDateTime effectiveTo, String status
) {}
