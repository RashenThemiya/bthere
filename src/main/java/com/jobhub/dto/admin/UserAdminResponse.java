package com.jobhub.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

public record UserAdminResponse(
        Long id, String username, String email, String phoneNumber,
        boolean emailVerified, boolean phoneVerified, String status,
        List<String> roles, LocalDateTime lastLoginAt, LocalDateTime createdAt
) {}
