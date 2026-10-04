package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotNull;

public record UpdateProviderMarketRequest(
        @NotNull(message = "Market ID is required") Long marketId
) {
}
