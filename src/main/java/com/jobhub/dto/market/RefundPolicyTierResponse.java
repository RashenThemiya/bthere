package com.jobhub.dto.market;

import java.math.BigDecimal;

public record RefundPolicyTierResponse(
        Long tierId,
        String name,
        Integer minimumMinutesBeforeStart,
        BigDecimal refundPercentage,
        BigDecimal cancellationFee,
        Integer priority
) {
}
