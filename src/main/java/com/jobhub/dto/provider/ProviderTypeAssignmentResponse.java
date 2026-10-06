package com.jobhub.dto.provider;

import java.math.BigDecimal;

public record ProviderTypeAssignmentResponse(
        Long assignmentId,
        Long providerTypeId,
        String providerTypeName,
        BigDecimal experienceYears,
        String status,
        String providerStatus,
        String adminStatus,
        String verificationStatus,
        boolean emergencyOverride,
        String emergencyOverrideReason,
        java.time.LocalDateTime emergencyOverrideExpiresAt,
        java.util.List<Long> requiredDocumentTypeIds,
        java.util.List<Long> missingDocumentTypeIds
) {
}
