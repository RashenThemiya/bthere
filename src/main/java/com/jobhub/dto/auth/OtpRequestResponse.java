package com.jobhub.dto.auth;

public record OtpRequestResponse(
        String message,
        long expiresIn
) {
}
