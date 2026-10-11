package com.jobhub.service.provider;

import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.entity.provider.ServiceProviderMarket;
import com.jobhub.repository.provider.ServiceProviderMarketRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderMarketLookupServiceTest {

    @Mock private ServiceProviderRepository providerRepository;
    @Mock private ServiceProviderMarketRepository providerMarketRepository;

    @InjectMocks
    private ProviderMarketLookupService service;

    @Test
    void returnsActiveMarketForProvider() {
        ServiceProvider provider = new ServiceProvider();
        provider.setServiceProviderId(12L);
        ServiceProviderMarket market = new ServiceProviderMarket();
        market.setServiceProviderId(12L);
        market.setMarketId(3L);
        market.setStatus("ACTIVE");

        when(providerRepository.findByUserId(25L)).thenReturn(Optional.of(provider));
        when(providerMarketRepository.findByServiceProviderIdAndStatus(12L, "ACTIVE"))
                .thenReturn(Optional.of(market));

        assertThat(service.findActiveMarketId(25L)).isEqualTo(3L);
    }

    @Test
    void returnsNullBeforeProviderSelectsMarket() {
        ServiceProvider provider = new ServiceProvider();
        provider.setServiceProviderId(12L);
        when(providerRepository.findByUserId(25L)).thenReturn(Optional.of(provider));
        when(providerMarketRepository.findByServiceProviderIdAndStatus(12L, "ACTIVE"))
                .thenReturn(Optional.empty());

        assertThat(service.findActiveMarketId(25L)).isNull();
    }
}
