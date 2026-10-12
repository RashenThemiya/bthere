package com.jobhub.dto.provider;

public record ProviderTypeResponse(
        Long id,
        String name,
        String description,
        String iconUrl,
        String status,
        ServiceMediaPayload icon,
        ServiceMediaPayload coverImage,
        java.util.List<ServiceMediaPayload> gallery,
        java.util.List<ServiceResponsibilityPayload> responsibilities
) {
}
