package com.jobhub.entity.engagement;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_bonus")
@Getter
@Setter
@NoArgsConstructor
public class UserBonus extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userBonusId;
    private Long bonusCampaignId;
    private Long userId;
    private BigDecimal amount;
    private String status;
    private Long walletTransactionId;
    private LocalDateTime awardedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}

