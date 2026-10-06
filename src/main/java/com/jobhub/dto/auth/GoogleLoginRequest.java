package com.jobhub.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GoogleLoginRequest(
        @NotBlank(message = "Google ID token is required")
        String idToken,

        @NotNull(message = "Account type is required")
        RegistrationType accountType
) {
}
