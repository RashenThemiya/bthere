package com.jobhub.entity.job;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "job")
@Getter
@Setter
@NoArgsConstructor
public class Job extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long jobId;
    private Long marketId;
    private String currencyCode;
    private Long customerId;
    private Long serviceProviderTypeId;
    private Long assignedServiceProviderId;
    private Long rateId;
    private LocalDateTime startDatetime;
    private LocalDateTime expectedEndDatetime;
    private LocalDateTime actualStartDatetime;
    private LocalDateTime actualEndDatetime;
    private String locationType;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String district;
    private String province;
    private String postalCode;
    private String country;
    @Column(columnDefinition = "TEXT")
    private String customerNote;
    private BigDecimal expectedAmount;
    private BigDecimal actualAmount;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal platformFee;
    private BigDecimal totalAmount;
    private String jobStatus;
    private String paymentStatus;
}

