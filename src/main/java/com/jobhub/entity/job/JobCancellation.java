package com.jobhub.entity.job;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/** One row per cancelled paid booking: who cancelled, the policy snapshot used, and where the money went. */
@Entity
@Table(name = "job_cancellation", uniqueConstraints =
        @UniqueConstraint(name = "uk_job_cancellation_job", columnNames = "job_id"))
@Getter
@Setter
@NoArgsConstructor
public class JobCancellation extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cancellationId;
    @Column(name = "job_id")
    private Long jobId;
    private Long requestId;
    private Long cancelledByUserId;
    /** CUSTOMER or PROVIDER. */
    @Column(length = 20)
    private String cancelledBy;
    private String reason;
    @Column(columnDefinition = "TEXT")
    private String note;
    @Column(length = 3)
    private String currencyCode;
    private BigDecimal paidAmount;
    private BigDecimal cancellationFee;
    @Column(precision = 5, scale = 2)
    private BigDecimal refundPercentage;
    private BigDecimal refundAmount;
    private BigDecimal providerAmount;
    private BigDecimal platformAmount;
    /** WALLET: refunds are credited to the customer's wallet. */
    @Column(length = 20)
    private String refundDestination;
    private Long minutesBeforeStart;
    private Long refundPolicyId;
    private Integer refundPolicyVersion;
    @Column(length = 100)
    private String refundTierName;
    private Long walletOperationId;
}
