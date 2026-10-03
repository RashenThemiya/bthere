package com.jobhub.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAdminRequest(
        @NotBlank(message = "Username is required")
        @Pattern(
                regexp = "^[A-Za-z0-9._-]{3,50}$",
                message = "Username must be 3-50 characters and contain only letters, numbers, dot, underscore or hyphen"
        )
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @Pattern(
                regexp = "^\\+[1-9][0-9]{7,14}$",
                message = "Phone number must use international E.164 format"
        )
        String phoneNumber,

        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 72, message = "Password must contain between 12 and 72 characters")
        String password,

        @NotNull(message = "Admin role is required")
        AdminRole role
) {
}
