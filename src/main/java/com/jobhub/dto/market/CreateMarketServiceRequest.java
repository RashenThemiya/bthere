package com.jobhub.dto.market;

import jakarta.validation.constraints.NotNull;

public record CreateMarketServiceRequest(
        @NotNull Long marketId,
        @NotNull Long serviceTypeId
) {}
