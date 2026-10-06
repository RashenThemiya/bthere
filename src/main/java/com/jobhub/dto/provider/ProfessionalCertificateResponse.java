package com.jobhub.dto.provider;

import java.time.LocalDate;

public record ProfessionalCertificateResponse(
        Long certificateId, Long assignmentId, String name,
        String issuingOrganization, String certificateNumber,
        LocalDate issuedDate, LocalDate expiryDate, String documentKey,
        String verificationStatus, String rejectionReason
) {}
