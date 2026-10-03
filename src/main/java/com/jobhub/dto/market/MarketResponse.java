package com.jobhub.dto.market;

import java.time.LocalDateTime;

public record MarketResponse(
        Long id,
        String name,
        String countryCode,
        String defaultCurrency,
        String timezone,
        String locale,
        String phoneCountryCode,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
