package com.jobhub.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SmsGatewaySettingsRequest(
        @Size(max = 4096, message = "API token must not exceed 4096 characters")
        String apiToken,

        @NotBlank(message = "Sender ID is required")
        @Size(max = 11, message = "Sender ID must not exceed 11 characters")
        String senderId,

        boolean enabled
) {
}
