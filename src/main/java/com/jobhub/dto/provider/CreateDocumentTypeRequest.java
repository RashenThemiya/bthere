package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDocumentTypeRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must not exceed 150 characters")
        String name,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        boolean hasExpiry,
        @jakarta.validation.constraints.Pattern(regexp = "HIDDEN|OPTIONAL|REQUIRED")
        String issuedDateMode,
        @jakarta.validation.constraints.Pattern(regexp = "HIDDEN|OPTIONAL|REQUIRED")
        String expiryDateMode,
        java.util.List<@jakarta.validation.Valid DocumentPartDefinition> parts
) {
    public CreateDocumentTypeRequest(String name, String description, boolean hasExpiry,
                                     String issuedDateMode, String expiryDateMode) {
        this(name, description, hasExpiry, issuedDateMode, expiryDateMode, null);
    }
}
