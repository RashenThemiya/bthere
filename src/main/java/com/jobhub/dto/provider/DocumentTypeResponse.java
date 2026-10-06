package com.jobhub.dto.provider;

public record DocumentTypeResponse(
        Long id,
        String name,
        String description,
        boolean hasExpiry,
        String status
) {
}
