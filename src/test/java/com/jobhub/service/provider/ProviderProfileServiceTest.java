package com.jobhub.service.provider;

import com.jobhub.dto.provider.ProviderProfileRequest;
import com.jobhub.dto.provider.ProviderProfileResponse;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.entity.provider.ServiceProviderMarket;
import com.jobhub.entity.market.Market;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.provider.ServiceProviderMarketRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderProfileServiceTest {

    @Mock
    private ServiceProviderRepository serviceProviderRepository;

    @Mock
    private ServiceProviderMarketRepository providerMarketRepository;

    @Mock
    private MarketRepository marketRepository;

    @InjectMocks
    private ProviderProfileService providerProfileService;

    @Test
    void createsProviderPendingVerification() {
        ProviderProfileRequest request = new ProviderProfileRequest(
                "John",
                "Smith",
                "901234567V",
                null,
                LocalDate.of(1990, 1, 1),
                "MALE",
                null,
                "Experienced electrician",
                "AVAILABLE",
                1L
        );
        when(serviceProviderRepository.findByUserId(8L)).thenReturn(Optional.empty());
        when(serviceProviderRepository.save(any(ServiceProvider.class))).thenAnswer(invocation -> {
            ServiceProvider provider = invocation.getArgument(0);
            provider.setServiceProviderId(14L);
            return provider;
        });
        Market market = new Market();
        market.setStatus("ACTIVE");
        when(marketRepository.findById(1L)).thenReturn(Optional.of(market));
        ServiceProviderMarket providerMarket = new ServiceProviderMarket();
        providerMarket.setMarketId(1L);
        when(providerMarketRepository.findByServiceProviderIdAndStatus(14L, "ACTIVE"))
                .thenReturn(Optional.of(providerMarket));

        ProviderProfileResponse response = providerProfileService.upsert(8L, request);

        assertThat(response.serviceProviderId()).isEqualTo(14L);
        assertThat(response.verificationStatus()).isEqualTo("NOT_SUBMITTED");
        assertThat(response.averageRating()).isZero();
        assertThat(response.profileCompleted()).isTrue();
    }
}
