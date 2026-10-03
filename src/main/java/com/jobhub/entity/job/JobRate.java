package com.jobhub.entity.job;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_rate")
@Getter
@Setter
@NoArgsConstructor
public class JobRate extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rateId;
    private Long marketId;
    private String currencyCode;
    private Long serviceProviderTypeId;
    private String billingType;
    private Integer durationMinutes;
    private BigDecimal rate;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String status;
}

