package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateProviderSkillsRequest(
        @NotNull(message = "Skill IDs are required")
        @Size(max = 100, message = "Too many skills selected")
        List<@NotNull(message = "Skill ID is required") Long> skillIds
) {}
