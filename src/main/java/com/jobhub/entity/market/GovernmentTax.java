package com.jobhub.entity.market;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "government_tax")
@Getter
@Setter
@NoArgsConstructor
public class GovernmentTax extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taxId;

    private Long marketId;
    private String taxName;
    private String taxNumber;
    private BigDecimal rate;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status;
}
