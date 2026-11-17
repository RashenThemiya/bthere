package com.jobhub.entity.job;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A provider's complaint that the customer did not show up (no start code given / could not be reached) on a
 * paid, time-based booking. The booking waits in NO_SHOW_REVIEW until an admin approves (customer charged per the
 * refund policy, provider paid a share) or rejects (customer refunded in full, optional provider penalty).
 */
@Entity
@Table(name = "no_show_claim", uniqueConstraints = @UniqueConstraint(name = "uk_no_show_claim_job", columnNames = "job_id"),
        indexes = @Index(name = "idx_no_show_claim_status", columnList = "status"))
@Getter
@Setter
@NoArgsConstructor
public class NoShowClaim extends CreatedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long claimId;
    @Column(name = "job_id", nullable = false)
    private Long jobId;
    @Column(nullable = false)
    private Long requestId;
    @Column(nullable = false)
    private Long serviceProviderId;
    @Column(nullable = false)
    private Long providerUserId;
    @Column(nullable = false)
    private Long customerUserId;
    /** PENDING, APPROVED or REJECTED. */
    @Column(nullable = false, length = 20)
    private String status;

    // ---------- evidence ----------
    @Column(nullable = false, length = 300)
    private String photoKey;
    /** Server-side upload time of the photo (S3), not the phone's clock. */
    private Instant photoUploadedAt;
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;
    private Integer accuracyMeters;
    /** Distance to the booking location; null when the booking has no location. */
    private Integer distanceMeters;
    private boolean distanceFlagged;
    /** The phone's own capture time, kept for comparison only. */
    private Instant deviceCapturedAt;
    @Column(nullable = false)
    private Instant submittedAt;
    /** Minutes between the booked start and submission. */
    private Long minutesAfterStart;
    private Integer callAttempts;
    @Column(columnDefinition = "TEXT")
    private String providerNote;

    // ---------- customer's side ----------
    @Column(columnDefinition = "TEXT")
    private String customerNote;
    private Instant customerRespondedAt;

    // ---------- decision ----------
    private Long decidedByUserId;
    private Instant decidedAt;
    @Column(columnDefinition = "TEXT")
    private String decisionReason;
    @Column(length = 3)
    private String currencyCode;
    @Column(precision = 5, scale = 2)
    private BigDecimal refundPercentage;
    @Column(precision = 38, scale = 2)
    private BigDecimal refundAmount;
    @Column(precision = 38, scale = 2)
    private BigDecimal providerAmount;
    @Column(precision = 38, scale = 2)
    private BigDecimal platformAmount;
    @Column(precision = 38, scale = 2)
    private BigDecimal penaltyAmount;
    private Long penaltyId;
    private Long cancellationId;
}
