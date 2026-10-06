package com.jobhub.dto.upload;

import java.time.Instant;

public record PresignedUrlResponse(
        String key,
        String url,
        Instant expiresAt
) {
}
