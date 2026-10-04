package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotBlank;

public record UpdateProviderServiceStatusRequest(
        @NotBlank(message = "Status is required") String status
) {
}
