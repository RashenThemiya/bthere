package com.jobhub.dto.market;

import jakarta.validation.constraints.NotBlank;

public record UpdateMarketStatusRequest(
        @NotBlank(message = "Status is required") String status
) {
}
