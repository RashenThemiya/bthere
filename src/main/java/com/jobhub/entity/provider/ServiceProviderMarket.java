package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "service_provider_market",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_service_provider_market",
                columnNames = "service_provider_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ServiceProviderMarket extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long providerMarketId;

    private Long serviceProviderId;

    private Long marketId;

    private String status;
}
