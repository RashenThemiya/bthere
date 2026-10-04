package com.jobhub.dto.provider;

import java.util.List;

public record ServiceSetupResponse(
        Long serviceTypeId,
        String name,
        String description,
        String iconUrl,
        String status,
        List<DocumentRequirement> documents,
        List<SkillRequirement> skills,
        SectionRequirement certificates,
        SectionRequirement education
) {
    public record DocumentRequirement(
            Long documentTypeId,
            String documentTypeName,
            boolean required,
            boolean requiresApproval
    ) {
    }

    public record SkillRequirement(
            Long skillId,
            String name,
            boolean required,
            boolean requiresApproval
    ) {
    }

    public record SectionRequirement(String level, boolean requiresApproval) {
    }
}
