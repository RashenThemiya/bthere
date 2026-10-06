package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProviderDocumentRequest(
        @NotNull(message = "Document type ID is required")
        Long documentTypeId,

        @NotBlank(message = "Document name is required")
        @Size(max = 255, message = "Document name must not exceed 255 characters")
        String documentName,

        @Size(max = 150, message = "Document number must not exceed 150 characters")
        String documentNumber,

        @NotBlank(message = "Document key is required")
        @Size(max = 2048, message = "Document key must not exceed 2048 characters")
        @Pattern(
                regexp = "^(?!.*\\.\\.)providers/[A-Za-z0-9/_-]+\\.[A-Za-z0-9]+$",
                message = "Document key must be a relative provider S3 key"
        )
        String documentKey,

        LocalDate issuedDate,
        LocalDate expiryDate
) {
}
