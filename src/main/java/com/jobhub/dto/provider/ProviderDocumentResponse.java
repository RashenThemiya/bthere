package com.jobhub.dto.provider;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProviderDocumentResponse(
        Long documentId,
        Long serviceProviderId,
        Long documentTypeId,
        String documentTypeName,
        String documentName,
        String documentNumber,
        String documentUrl,
        LocalDate issuedDate,
        LocalDate expiryDate,
        String verificationStatus,
        Long verifiedBy,
        LocalDateTime verifiedAt,
        String rejectionReason,
        LocalDateTime createdAt
) {
}
