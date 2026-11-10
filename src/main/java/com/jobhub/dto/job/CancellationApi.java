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
                         @DecimalMin("0.00") BigDecimal expectedRefundAmount,
                         @DecimalMin("0.00") BigDecimal expectedPenaltyAmount) {
        public Cancel(String reason, String note, BigDecimal expectedRefundAmount) {
            this(reason, note, expectedRefundAmount, null);
        }
    }

    /** What a cancellation would do now. cancelledBy: CUSTOMER or PROVIDER. refundDestination: WALLET. */
    public record Quote(Long jobId, String cancelledBy, String currencyCode, BigDecimal paidAmount,
                        BigDecimal refundAmount, BigDecimal refundPercentage, BigDecimal cancellationFee,
                        BigDecimal retainedAmount, BigDecimal providerAmount, BigDecimal platformAmount,
                        boolean platformFeeRefunded, String refundDestination, long minutesBeforeStart,
                        Long refundPolicyId, Integer refundPolicyVersion, String refundTierName,
                        BigDecimal penaltyAmount, BigDecimal penaltyPercentage, BigDecimal penaltyFee,
                        BigDecimal penaltyCustomerAmount, String penaltyTierName, boolean penaltyGraceApplied) {
        public Quote(Long jobId, String cancelledBy, String currencyCode, BigDecimal paidAmount,
                     BigDecimal refundAmount, BigDecimal refundPercentage, BigDecimal cancellationFee,
                     BigDecimal retainedAmount, BigDecimal providerAmount, BigDecimal platformAmount,
                     boolean platformFeeRefunded, String refundDestination, long minutesBeforeStart,
                     Long refundPolicyId, Integer refundPolicyVersion, String refundTierName) {
            this(jobId, cancelledBy, currencyCode, paidAmount, refundAmount, refundPercentage, cancellationFee,
                    retainedAmount, providerAmount, platformAmount, platformFeeRefunded, refundDestination,
                    minutesBeforeStart, refundPolicyId, refundPolicyVersion, refundTierName,
                    BigDecimal.ZERO.setScale(2), null, null, BigDecimal.ZERO.setScale(2), null, false);
        }
    }

    public record Cancellation(Long cancellationId, Long jobId, Long requestId, String cancelledBy, Long cancelledByUserId,
                               String reason, String note, String currencyCode, BigDecimal paidAmount,
                               BigDecimal refundAmount, BigDecimal refundPercentage, BigDecimal cancellationFee,
                               BigDecimal providerAmount, BigDecimal platformAmount, String refundDestination,
                               Long minutesBeforeStart, Long refundPolicyId, Integer refundPolicyVersion,
                               String refundTierName, Long walletOperationId, String paymentStatus,
                               LocalDateTime createdAt, Penalty penalty, boolean providerSuspended) {}

    /**
     * Provider penalty. status: OUTSTANDING (part still owed; recovered from later earnings), COLLECTED or WAIVED.
     * customerShareAmount goes to the customer's wallet as it is collected.
     */
    public record Penalty(Long penaltyId, Long jobId, Long serviceProviderId, String currencyCode,
                          BigDecimal penaltyAmount, BigDecimal penaltyPercentage, BigDecimal penaltyFee, String tierName,
                          BigDecimal customerShareAmount, BigDecimal customerPaidAmount, BigDecimal collectedAmount,
                          BigDecimal outstandingAmount, String status, Long refundPolicyId, Integer refundPolicyVersion,
                          String waiveReason, java.time.Instant waivedAt, LocalDateTime createdAt) {}

    /** A provider's penalties with the total still owed; withdrawals are blocked while outstandingTotal > 0. */
    public record PenaltySummary(String currencyCode, BigDecimal outstandingTotal, String providerStatus,
                                 java.util.List<Penalty> penalties) {}

    public record Waive(@NotBlank @Size(max = 500) String reason) {}

    public record Reactivate(@Size(max = 500) String note) {}
}
