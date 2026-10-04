package com.jobhub.dto.customer;

public record CustomerProfileResponse(
        Long customerId,
        Long userId,
        String firstName,
        String lastName,
        String profilePhoto,
        String addressLine1,
        String addressLine2,
        String city,
        String district,
        String province,
        String postalCode,
        String country,
        String status,
        boolean profileCompleted
) {
}
