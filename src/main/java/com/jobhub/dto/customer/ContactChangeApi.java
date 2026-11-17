package com.jobhub.dto.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Signed-in customer: change or verify the account email / phone with a one-time code. */
public final class ContactChangeApi {
    private ContactChangeApi() {}

    public record EmailCodeRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            @Size(max = 255, message = "Email is too long")
            String email
    ) {}

    public record EmailVerifyRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            @Size(max = 255, message = "Email is too long")
            String email,
            @NotBlank(message = "OTP is required")
            @Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain six digits")
            String otp,
            /** Required when the account has a password; ignored for phone-OTP and Google accounts. */
            String currentPassword
    ) {}

    public record PhoneCodeRequest(
            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "Phone number must use international E.164 format")
            String phoneNumber
    ) {}

    public record PhoneVerifyRequest(
            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "Phone number must use international E.164 format")
            String phoneNumber,
            @NotBlank(message = "OTP is required")
            @Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain six digits")
            String otp,
            /** Required when the account has a password; ignored for phone-OTP and Google accounts. */
            String currentPassword
    ) {}

    /** The code was sent. {@code resendAfterSeconds} is when another code may be requested. */
    public record CodeSent(String destination, long expiresIn, long resendAfterSeconds) {}
}
