package com.jobhub.entity.engagement;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promo_redemption")
@Getter
@Setter
@NoArgsConstructor
public class PromoRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long redemptionId;
    private Long promoCodeId;
    private Long userId;
    private Long jobId;
    private BigDecimal discountAmount;
    private LocalDateTime redeemedAt;
}

