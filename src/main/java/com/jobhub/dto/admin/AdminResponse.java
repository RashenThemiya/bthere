package com.jobhub.dto.admin;

import java.time.LocalDateTime;

public record AdminResponse(
        Long id,
        String username,
        String email,
        String phoneNumber,
        String status,
        AdminRole role,
        LocalDateTime createdAt
) {
}
