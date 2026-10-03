package com.jobhub.entity.engagement;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "referral_code")
@Getter
@Setter
@NoArgsConstructor
public class ReferralCode extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long referralCodeId;
    private Long userId;
    private String code;
    private String status;
}

