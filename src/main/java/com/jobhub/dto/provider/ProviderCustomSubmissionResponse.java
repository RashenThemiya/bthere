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
        String reviewNote,
        String reuseScope,
        boolean reused,
        Long sourceAssignmentId,
        String valueSource,
        String profileField,
        boolean readOnly
) {
    public ProviderCustomSubmissionResponse(Long submissionId, Long assignmentId, Long fieldId,
            String fieldCode, String fieldLabel, JsonNode value, String verificationStatus,
            Long reviewedBy, LocalDateTime reviewedAt, String reviewNote, String reuseScope,
            boolean reused, Long sourceAssignmentId) {
        this(submissionId, assignmentId, fieldId, fieldCode, fieldLabel, value, verificationStatus,
                reviewedBy, reviewedAt, reviewNote, reuseScope, reused, sourceAssignmentId, "MANUAL", null, false);
    }
}
