package com.jobhub.entity.engagement;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "referral_program")
@Getter
@Setter
@NoArgsConstructor
public class ReferralProgram extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long referralProgramId;
    private Long marketId;
    private String currencyCode;
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    private BigDecimal referrerReward;
    private BigDecimal referredUserReward;
    private String rewardTrigger;
    private String targetType;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer maxReferralsPerUser;
    private String status;
}

