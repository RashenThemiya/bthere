package com.jobhub.dto.auth;

import java.util.List;

public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        String status,
        Long marketId,
        List<String> roles
) {
}
