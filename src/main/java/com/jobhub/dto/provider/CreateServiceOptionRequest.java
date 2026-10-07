package com.jobhub.dto.provider;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateServiceOptionRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @Min(0) Integer displayOrder,
        String schedulingModel,
        @Min(1) Integer routeAverageSpeedKmh,
        boolean locationEnabled,
        Set<@NotBlank String> deliveryModes,
        String fulfillmentModel,
        @Min(1) Integer defaultCapacity,
        @Min(1) Integer requiredProviderCount,
        String bookingMode,
        String providerSelectionMode,
        String pricingOwner
) {}
