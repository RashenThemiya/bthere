package com.jobhub.entity.job;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "booking_offer",indexes={
        @Index(name="idx_booking_offer_request",columnList="request_id,status,expires_at"),
        @Index(name="idx_booking_offer_provider",columnList="provider_id,status")}) @Getter @Setter @NoArgsConstructor
public class BookingOffer extends CreatedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long offerId;
    private Long requestId;
    private Long providerId;
    private Long providerLocationId;
    private String status;
    private Instant expiresAt;
    @Column(precision=38,scale=2) private java.math.BigDecimal quotedAmount;
    private String currencyCode;
    @Column(precision=38,scale=2) private java.math.BigDecimal platformFeePercentage;
    @Column(precision=38,scale=2) private java.math.BigDecimal platformFee;
    @Column(precision=38,scale=2) private java.math.BigDecimal providerEarnings;
}
