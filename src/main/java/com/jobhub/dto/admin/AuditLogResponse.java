package com.jobhub.dto.admin;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id, Long actorUserId, String action, String entityType,
        Long entityId, String oldValue, String newValue,
        String ipAddress, LocalDateTime createdAt
) {}
