package com.jobhub.entity.engagement;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotion")
@Getter
@Setter
@NoArgsConstructor
public class Promotion extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long promotionId;
    private Long marketId;
    private String currencyCode;
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal minimumOrderAmount;
    private BigDecimal maximumDiscountAmount;
    private boolean newUsersOnly;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer totalUsageLimit;
    private Integer usageLimitPerUser;
    private String status;
}

