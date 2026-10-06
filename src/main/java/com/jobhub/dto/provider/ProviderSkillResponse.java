package com.jobhub.dto.provider;

public record ProviderSkillResponse(
        Long providerSkillId,
        Long assignmentId,
        Long skillId,
        String skillName,
        boolean required,
        String verificationStatus,
        String rejectionReason
) {}
