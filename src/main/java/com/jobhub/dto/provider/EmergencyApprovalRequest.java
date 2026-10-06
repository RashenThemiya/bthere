package com.jobhub.dto.provider;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record EmergencyApprovalRequest(
        @NotBlank @Size(max = 2000) String reason,
        @Future LocalDateTime expiresAt
) {}
