package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "service_provider_service_area")
@Getter
@Setter
@NoArgsConstructor
public class ServiceProviderServiceArea extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long serviceAreaId;
    private Long marketId;
    private Long serviceProviderId;
    private String country;
    private String province;
    private String district;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal radiusKm;
    private String status;
}

