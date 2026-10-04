package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProviderProfileRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @Size(max = 100, message = "NIC number must not exceed 100 characters")
        String nicNumber,

        @Size(max = 100, message = "Passport number must not exceed 100 characters")
        String passportNumber,

        @NotNull(message = "Date of birth is required")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @NotBlank(message = "Gender is required")
        @Size(max = 50, message = "Gender must not exceed 50 characters")
        String gender,

        @Size(max = 2048, message = "Profile photo URL must not exceed 2048 characters")
        String profilePhoto,

        @Size(max = 5000, message = "Bio must not exceed 5000 characters")
        String bio,

        @NotBlank(message = "Availability status is required")
        @Pattern(
                regexp = "^(AVAILABLE|UNAVAILABLE|OFFLINE)$",
                message = "Availability status must be AVAILABLE, UNAVAILABLE or OFFLINE"
        )
        String availabilityStatus,

        @NotNull(message = "Market ID is required")
        Long marketId
) {
}
