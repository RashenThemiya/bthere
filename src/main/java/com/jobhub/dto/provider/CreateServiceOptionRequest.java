package com.jobhub.dto.provider;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.List;
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
        String pricingOwner,
        Set<@NotBlank String> allowedPaymentMethods,
        @Valid ServiceMediaPayload icon,
        @Valid ServiceMediaPayload coverImage,
        @NotNull(message = "Gallery is required") List<@Valid ServiceMediaPayload> gallery,
        @NotNull(message = "Responsibilities are required")
        List<@Valid ServiceResponsibilityPayload> responsibilities,
        List<@NotNull @Valid OptionDynamicFieldRequest> dynamicFields,
        @Valid ServiceMediaPayload mapMarkerIcon,
        @jakarta.validation.constraints.Positive Long mapMarkerFieldId
) {
    public CreateServiceOptionRequest(String code, String name, String description, Integer displayOrder,
            String schedulingModel, Integer routeAverageSpeedKmh, boolean locationEnabled,
            Set<String> deliveryModes, String fulfillmentModel, Integer defaultCapacity,
            Integer requiredProviderCount, String bookingMode, String providerSelectionMode, String pricingOwner,
            Set<String> allowedPaymentMethods, ServiceMediaPayload icon, ServiceMediaPayload coverImage,
            List<ServiceMediaPayload> gallery, List<ServiceResponsibilityPayload> responsibilities,
            List<OptionDynamicFieldRequest> dynamicFields) {
        this(code, name, description, displayOrder, schedulingModel, routeAverageSpeedKmh, locationEnabled,
                deliveryModes, fulfillmentModel, defaultCapacity, requiredProviderCount, bookingMode,
                providerSelectionMode, pricingOwner, allowedPaymentMethods, icon, coverImage, gallery,
                responsibilities, dynamicFields, null, null);
    }
}
