package com.jobhub.dto.provider;

public record ServiceDocumentRequirementResponse(
        Long documentTypeId,
        String documentTypeName,
        String description,
        boolean hasExpiry,
        Long optionId,
        boolean required,
        boolean requiresApproval
) {
}
