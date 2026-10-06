package com.jobhub.entity.market;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "market_service_offering", uniqueConstraints = @UniqueConstraint(
        name = "uk_market_service_offering", columnNames = {"market_id", "service_provider_type_id"}))
@Getter @Setter @NoArgsConstructor
public class MarketServiceOffering extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long offeringId;
    private Long marketId;
    private Long serviceProviderTypeId;
    private String currencyCode;
    private String status;
}
