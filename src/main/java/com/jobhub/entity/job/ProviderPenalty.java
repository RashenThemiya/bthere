package com.jobhub.entity.job;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Penalty charged to a provider for cancelling a paid booking. Collected from the provider's available wallet
 * balance; whatever cannot be collected stays OUTSTANDING and is recovered from later earnings. The customer's
 * share is paid before the platform's.
 */
@Entity
@Table(name = "provider_penalty", uniqueConstraints =
        @UniqueConstraint(name = "uk_provider_penalty_job", columnNames = "job_id"))
@Getter
@Setter
@NoArgsConstructor
public class ProviderPenalty extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long penaltyId;
    @Column(name = "job_id", nullable = false)
    private Long jobId;
    private Long cancellationId;
    @Column(nullable = false)
    private Long serviceProviderId;
    @Column(nullable = false)
    private Long providerUserId;
    @Column(nullable = false)
    private Long customerUserId;
    @Column(nullable = false, length = 3)
    private String currencyCode;
    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal penaltyAmount;
    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal customerShareAmount;
    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal collectedAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal customerPaidAmount = BigDecimal.ZERO;
    @Column(precision = 5, scale = 2)
    private BigDecimal penaltyPercentage;
    @Column(precision = 12, scale = 2)
    private BigDecimal penaltyFee;
    @Column(length = 100)
    private String tierName;
    private Long refundPolicyId;
    private Integer refundPolicyVersion;
    /** Wallet funding mode of the first collection; later collections and a waiver use the same mode. */
    @Column(length = 30)
    private String fundingMode;
    private int collectionCount;
    private Long lastOperationId;
    /** OUTSTANDING, COLLECTED or WAIVED. */
    @Column(nullable = false, length = 20)
    private String status;
    private Instant collectedAt;
    private Long waivedByUserId;
    @Column(length = 500)
    private String waiveReason;
    private Instant waivedAt;

    public BigDecimal outstanding() {
        return "OUTSTANDING".equals(status) ? penaltyAmount.subtract(collectedAmount) : BigDecimal.ZERO.setScale(2);
    }
}
