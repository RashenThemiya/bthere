package com.jobhub.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^\\+[1-9][0-9]{7,14}$",
                message = "Phone number must use international E.164 format"
        )
        String phoneNumber,

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain six digits")
        String otp,

        @NotNull(message = "Account type is required")
        RegistrationType accountType
) {
}
