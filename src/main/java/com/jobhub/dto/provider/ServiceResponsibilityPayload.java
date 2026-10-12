package com.jobhub.dto.provider;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ServiceResponsibilityPayload(
        @NotBlank
        @Pattern(regexp = "PROVIDER|CUSTOMER|PLATFORM",
                message = "Responsible party must be PROVIDER, CUSTOMER or PLATFORM")
        String responsibleParty,
        @NotBlank @Size(max = 150) String title,
        @Size(max = 1000) String description,
        boolean required,
        @Min(0) Integer displayOrder
) {
}
