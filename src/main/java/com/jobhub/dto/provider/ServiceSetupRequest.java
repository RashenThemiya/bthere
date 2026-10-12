package com.jobhub.dto.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ServiceSetupRequest(
        @NotBlank(message = "Service name is required")
        @Size(max = 150, message = "Service name must not exceed 150 characters")
        String name,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @Size(max = 2048, message = "Icon URL must not exceed 2048 characters")
        String iconUrl,

        boolean availabilityEnabled,

        Boolean serviceAreasEnabled,

        boolean optionsRequired,

        Integer minimumOptionSelections,

        Integer maximumOptionSelections,

        @NotNull(message = "Document requirements are required")
        List<@Valid DocumentRequirement> documents,

        @NotNull(message = "Skills are required")
        List<@Valid SkillRequirement> skills,

        @NotNull(message = "Certificate configuration is required")
        @Valid SectionRequirement certificates,

        @NotNull(message = "Education configuration is required")
        @Valid SectionRequirement education,

        @Valid ServiceMediaPayload icon,

        @Valid ServiceMediaPayload coverImage,

        @NotNull(message = "Gallery is required")
        List<@Valid ServiceMediaPayload> gallery,

        @NotNull(message = "Responsibilities are required")
        List<@Valid ServiceResponsibilityPayload> responsibilities
) {
    public record DocumentRequirement(
            @NotNull(message = "Document type ID is required") Long documentTypeId,
            Long optionId,
            boolean required,
            boolean requiresApproval
    ) {
    }

    public record SkillRequirement(
            @NotBlank(message = "Skill name is required")
            @Size(max = 150, message = "Skill name must not exceed 150 characters")
            String name,
            boolean required,
            boolean requiresApproval
    ) {
    }

    public record SectionRequirement(
            @NotBlank(message = "Requirement level is required") String level,
            boolean requiresApproval
    ) {
    }
}
