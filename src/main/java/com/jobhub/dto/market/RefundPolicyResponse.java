package com.jobhub.dto.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record RefundPolicyResponse(
        Long policyId,
        Long marketId,
        Long offeringId,
        Integer version,
        String name,
        String description,
        String currencyCode,
        BigDecimal noProviderRefundPercentage,
        BigDecimal providerCancellationRefundPercentage,
        BigDecimal providerNoShowRefundPercentage,
        BigDecimal serviceFailureRefundPercentage,
        BigDecimal customerNoShowRefundPercentage,
        boolean platformFeeRefundable,
        boolean processingFeeRefundable,
        String status,
        Instant effectiveFrom,
        Instant effectiveTo,
        List<RefundPolicyTierResponse> tiers,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
