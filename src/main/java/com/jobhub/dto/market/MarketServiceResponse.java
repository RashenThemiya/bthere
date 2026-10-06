package com.jobhub.dto.market;

public record MarketServiceResponse(
        Long offeringId, Long marketId, String marketName,
        Long serviceTypeId, String serviceName, String currencyCode, String status
) {}
