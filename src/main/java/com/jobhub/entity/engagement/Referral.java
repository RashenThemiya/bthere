package com.jobhub.entity.engagement;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "referral")
@Getter
@Setter
@NoArgsConstructor
public class Referral extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long referralId;
    private Long referralProgramId;
    private Long referrerUserId;
    private Long referredUserId;
    private String referralCode;
    private String status;
    private LocalDateTime qualifiedAt;
    private LocalDateTime rewardedAt;
}

