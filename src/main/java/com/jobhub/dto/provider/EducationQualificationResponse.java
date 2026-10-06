package com.jobhub.dto.provider;

import java.time.LocalDate;

public record EducationQualificationResponse(
        Long educationId, Long assignmentId, String qualificationName,
        String instituteName, String fieldOfStudy, LocalDate startDate,
        LocalDate completionDate, String result, String documentKey,
        String verificationStatus, String rejectionReason
) {}
