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
    private Long serviceOptionId;
    private String billingType;
    private Integer durationMinutes;
    private BigDecimal rate;
    private BigDecimal baseFare;
    private BigDecimal pricePerKm;
    private BigDecimal minimumFare;
    private String geographicalAreaName;
    private BigDecimal areaLatitude;
    private BigDecimal areaLongitude;
    private BigDecimal areaRadiusKm;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String status;
}

