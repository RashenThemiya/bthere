package com.jobhub.dto.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProviderProfileResponse(
        Long serviceProviderId,
        Long userId,
        String firstName,
        String lastName,
        String nicNumber,
        String passportNumber,
        LocalDate dateOfBirth,
        String gender,
        String profilePhoto,
        String bio,
        String verificationStatus,
        String availabilityStatus,
        String status,
        BigDecimal averageRating,
        Integer totalRatings,
        Long marketId,
        boolean profileCompleted
) {
}
