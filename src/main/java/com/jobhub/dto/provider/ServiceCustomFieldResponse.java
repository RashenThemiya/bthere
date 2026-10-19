package com.jobhub.dto.provider;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record ServiceCustomFieldResponse(
        Long fieldId,
        Long serviceTypeId,
        Long optionId,
        List<Long> optionIds,
        String scope,
        String reuseScope,
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
        String status,
        boolean customerVisible,
        boolean searchable,
        String filterOperator,
        ServiceMediaPayload icon,
        String valueSource,
        String profileField
) {}
