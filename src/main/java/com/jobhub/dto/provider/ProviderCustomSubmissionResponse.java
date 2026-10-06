package com.jobhub.dto.provider;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record ProviderCustomSubmissionResponse(
        Long submissionId,
        Long assignmentId,
        Long fieldId,
        String fieldCode,
        String fieldLabel,
        JsonNode value,
        String verificationStatus,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String reviewNote
) {}
