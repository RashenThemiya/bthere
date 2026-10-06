package com.jobhub.dto.provider;

import java.math.BigDecimal;

public record ProviderServiceAreaResponse(
        Long serviceAreaId,
        Long optionId,
        String deliveryMode,
        String locationName,
        String country,
        String province,
        String district,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal radiusKm,
        Integer capacity,
        String status
) {}
