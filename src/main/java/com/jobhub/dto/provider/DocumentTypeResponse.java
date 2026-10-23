package com.jobhub.dto.provider;

public record DocumentTypeResponse(
        Long id,
        String name,
        String description,
        boolean hasExpiry,
        String status,
        String issuedDateMode,
        String expiryDateMode,
        java.util.List<DocumentPartDefinition> parts
) {
}
