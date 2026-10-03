package com.jobhub.security;

public record GoogleIdentity(
        String subject,
        String email,
        boolean emailVerified
) {
}
