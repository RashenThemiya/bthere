package com.jobhub.dto.admin;

import java.time.LocalDateTime;

public record SmsGatewaySettingsResponse(
        String provider,
        String senderId,
        boolean enabled,
        boolean apiTokenConfigured,
        String maskedApiToken,
        LocalDateTime updatedAt
) {
}
