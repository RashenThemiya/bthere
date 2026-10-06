package com.jobhub.dto.provider;

import java.time.LocalDateTime;

public record ProviderServiceOptionResponse(
        Long providerOptionId,
        Long optionId,
        String code,
        String name,
        String providerStatus,
        String adminStatus,
        String verificationStatus,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String reviewNote
) {}
