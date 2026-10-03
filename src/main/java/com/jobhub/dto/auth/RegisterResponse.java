package com.jobhub.dto.auth;

import java.util.List;

public record RegisterResponse(
        Long id,
        String username,
        String email,
        String phoneNumber,
        String status,
        boolean emailVerified,
        boolean phoneVerified,
        List<String> roles
) {
}
