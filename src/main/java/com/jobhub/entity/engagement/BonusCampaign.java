package com.jobhub.entity.engagement;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bonus_campaign")
@Getter
@Setter
@NoArgsConstructor
public class BonusCampaign extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bonusCampaignId;
    private Long marketId;
    private String currencyCode;
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String bonusType;
    private String targetType;
    private BigDecimal bonusAmount;
    private BigDecimal minimumJobAmount;
    private Integer expiryDays;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer maxTotalClaims;
    private Integer maxClaimsPerUser;
    private String status;
}

