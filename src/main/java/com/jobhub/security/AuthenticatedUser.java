package com.jobhub.security;

import java.util.List;

public record AuthenticatedUser(
        Long id,
        String username,
        String email,
        String status,
        List<String> roles
) {
}
