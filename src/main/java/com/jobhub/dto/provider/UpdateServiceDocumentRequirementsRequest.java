package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateServiceDocumentRequirementsRequest(
        @NotNull(message = "Document type IDs are required")
        @Size(max = 50, message = "A service cannot require more than 50 document types")
        List<@NotNull(message = "Document type ID is required") Long> documentTypeIds
) {
}
