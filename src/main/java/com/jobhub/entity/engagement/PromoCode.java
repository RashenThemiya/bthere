package com.jobhub.entity.engagement;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "promo_code")
@Getter
@Setter
@NoArgsConstructor
public class PromoCode extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long promoCodeId;
    private Long promotionId;
    private String code;
    private String status;
}

