package com.jobhub.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailResendRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email
) {
}
