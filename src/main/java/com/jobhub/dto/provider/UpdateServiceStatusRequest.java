package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotBlank;

public record UpdateServiceStatusRequest(
        @NotBlank(message = "Status is required") String status
) {
}
