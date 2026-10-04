package com.jobhub.dto.provider;

public record ProviderTypeResponse(
        Long id,
        String name,
        String description,
        String iconUrl,
        String status
) {
}
