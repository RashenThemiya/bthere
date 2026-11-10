package com.jobhub.dto.job;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CancellationApi {
    private CancellationApi() {}

    /**
     * expectedRefundAmount is optional: send the preview's refundAmount and the cancel is refused (409) if the
     * amount changed in between, for example because a refund tier boundary passed.
     */
    public record Cancel(@NotBlank @Size(max = 100) String reason, @Size(max = 1000) String note,
                         @DecimalMin("0.00") BigDecimal expectedRefundAmount) {}

    /** What a cancellation would do now. cancelledBy: CUSTOMER or PROVIDER. refundDestination: WALLET. */
    public record Quote(Long jobId, String cancelledBy, String currencyCode, BigDecimal paidAmount,
                        BigDecimal refundAmount, BigDecimal refundPercentage, BigDecimal cancellationFee,
                        BigDecimal retainedAmount, BigDecimal providerAmount, BigDecimal platformAmount,
                        boolean platformFeeRefunded, String refundDestination, long minutesBeforeStart,
                        Long refundPolicyId, Integer refundPolicyVersion, String refundTierName) {}

    public record Cancellation(Long cancellationId, Long jobId, Long requestId, String cancelledBy, Long cancelledByUserId,
                               String reason, String note, String currencyCode, BigDecimal paidAmount,
                               BigDecimal refundAmount, BigDecimal refundPercentage, BigDecimal cancellationFee,
                               BigDecimal providerAmount, BigDecimal platformAmount, String refundDestination,
                               Long minutesBeforeStart, Long refundPolicyId, Integer refundPolicyVersion,
                               String refundTierName, Long walletOperationId, String paymentStatus,
                               LocalDateTime createdAt) {}
}
