package com.jobhub.service.provider;

import com.jobhub.repository.provider.ServiceProviderMarketRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProviderMarketLookupService {

    private final ServiceProviderRepository providerRepository;
    private final ServiceProviderMarketRepository providerMarketRepository;

    @Transactional(readOnly = true)
    public Long findActiveMarketId(Long userId) {
        return providerRepository.findByUserId(userId)
                .flatMap(provider -> providerMarketRepository
                        .findByServiceProviderIdAndStatus(
                                provider.getServiceProviderId(),
                                "ACTIVE"
                        ))
                .map(providerMarket -> providerMarket.getMarketId())
                .orElse(null);
    }
}
