package com.jobhub.entity.engagement;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "referral_reward")
@Getter
@Setter
@NoArgsConstructor
public class ReferralReward extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long referralRewardId;
    private Long referralId;
    private Long userId;
    private String rewardType;
    private BigDecimal amount;
    private Long walletTransactionId;
    private String status;
}

