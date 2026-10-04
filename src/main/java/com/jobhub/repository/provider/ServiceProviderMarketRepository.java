package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceProviderMarket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceProviderMarketRepository
        extends JpaRepository<ServiceProviderMarket, Long> {

    Optional<ServiceProviderMarket> findByServiceProviderIdAndStatus(
            Long serviceProviderId,
            String status
    );

    void deleteAllByServiceProviderId(Long serviceProviderId);
}
