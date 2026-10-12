package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ServiceMediaPayload(
        @NotBlank @Size(max = 1024) String key,
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 300) String altText,
        Integer displayOrder
) {
}
