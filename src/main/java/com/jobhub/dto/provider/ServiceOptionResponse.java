package com.jobhub.dto.provider;

import java.util.Set;
import java.util.List;

public record ServiceOptionResponse(
        Long optionId,
        Long serviceTypeId,
        String code,
        String name,
        String description,
        Integer displayOrder,
        String status,
        String schedulingModel,
        Integer routeAverageSpeedKmh,
        boolean locationEnabled,
        Set<String> deliveryModes,
        String fulfillmentModel,
        Integer defaultCapacity,
        Integer requiredProviderCount,
        String bookingMode,
        String providerSelectionMode,
        List<DeliveryModeConfiguration> deliveryModeConfigurations
) {
    public record DeliveryModeConfiguration(
            String code,
            String providerLocationType,
            boolean providerLocationRequired,
            boolean radiusRequired,
            String customerLocationInput
    ) {}
}
