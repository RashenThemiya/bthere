package com.jobhub.entity.market;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "hospital")
@Getter
@Setter
@NoArgsConstructor
public class Hospital extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long hospitalId;

    private Long marketId;
    private String name;
    private String phoneNumber;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String district;
    private String province;
    private String postalCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String status;
}
