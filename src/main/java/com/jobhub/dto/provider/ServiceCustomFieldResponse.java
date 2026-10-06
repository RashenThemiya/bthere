package com.jobhub.dto.provider;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record ServiceCustomFieldResponse(
        Long fieldId,
        Long serviceTypeId,
        Long optionId,
        String scope,
        String code,
        String label,
        String description,
        String fieldType,
        boolean required,
        boolean requiresApproval,
        Integer minimumFiles,
        Integer maximumFiles,
        Integer maximumFileSizeMb,
        List<String> allowedFileTypes,
        JsonNode options,
        JsonNode condition,
        Integer displayOrder,
        Integer version,
        String status
) {}
