package com.jobhub.entity.job;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "booking_request", indexes = {
        @Index(name="idx_booking_request_status",columnList="status,expires_at"),
        @Index(name="idx_booking_request_confirmation",columnList="status,confirmation_expires_at"),
        @Index(name="idx_booking_request_payment",columnList="status,payment_started_at")}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_booking_request_key", columnNames = {"user_id", "idempotency_key"}),
        @UniqueConstraint(name = "uk_booking_request_order", columnNames = "payment_order_id")})
@Getter @Setter @NoArgsConstructor
public class BookingRequest extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long requestId;
    @Version private Long version;
    private Long userId;
    @Column(length = 100) private String idempotencyKey;
    private String payloadHash;
    private Long optionId;
    private Long marketId;
    private String selectionMode;
    @Column(columnDefinition = "LONGTEXT") private String payloadJson;
    @Column(columnDefinition = "LONGTEXT") private String candidatesJson;
    private int nextCandidate;
    private Long retryOfRequestId;
    private String status;
    private java.math.BigDecimal quotedAmount;
    @Column(precision=5,scale=2) private java.math.BigDecimal platformFeePercentage;
    private java.math.BigDecimal platformFee;
    private java.math.BigDecimal providerEarnings;
    private String currencyCode;
    @Column(unique=true) private Long jobId;
    private Instant expiresAt;
    private Instant confirmationExpiresAt;
    private Instant paymentDeadlineLimit;
    private Instant paymentStartedAt;
    @Column(length = 100) private String paymentOrderId;
    @Column(length = 100) private String gatewayPaymentId;
    private String paymentStatus;
    private String paymentFundingMode;
    private String settlementStatus;
    private Long settlementOperationId;
    private String checkoutMethod;
    @Column(columnDefinition = "TEXT") private String checkoutJson;
    private String startCode;
    private int startAttempts;
    private Instant codeExpiresAt;
}
