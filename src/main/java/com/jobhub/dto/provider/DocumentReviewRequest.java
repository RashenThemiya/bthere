package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DocumentReviewRequest(
        @NotBlank(message = "Review status is required")
        @Pattern(
                regexp = "^(APPROVED|REJECTED)$",
                message = "Review status must be APPROVED or REJECTED"
        )
        String status,

        @Size(max = 2000, message = "Rejection reason must not exceed 2000 characters")
        String rejectionReason
) {
}
