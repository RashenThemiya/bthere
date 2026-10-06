package com.jobhub.dto.market;

import java.time.LocalDateTime;

public record MarketResponse(
        Long id,
        String name,
        String countryCode,
        String currencyCode,
        String timezone,
        String locale,
        String phoneCode,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
