package com.jobhub.dto.provider;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;

import java.util.List;

public record ServiceCustomFieldRequest(
        Long optionId,
        @NotBlank String scope,
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 200) String label,
        @Size(max = 1000) String description,
        @NotBlank String fieldType,
        boolean required,
        boolean requiresApproval,
        @Min(0) Integer minimumFiles,
        @Min(1) Integer maximumFiles,
        @Min(1) @Max(100) Integer maximumFileSizeMb,
        List<@NotBlank String> allowedFileTypes,
        JsonNode options,
        JsonNode condition,
        @Min(0) Integer displayOrder
) {}
