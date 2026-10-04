package com.jobhub.service.provider;

import com.jobhub.dto.provider.ProviderProfileRequest;
import com.jobhub.dto.provider.ProviderProfileResponse;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.entity.provider.ServiceProviderMarket;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.repository.provider.ServiceProviderMarketRepository;
import com.jobhub.repository.market.MarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProviderProfileService {

    private final ServiceProviderRepository serviceProviderRepository;
    private final ServiceProviderMarketRepository providerMarketRepository;
    private final MarketRepository marketRepository;

    @Transactional(readOnly = true)
    public ProviderProfileResponse get(Long userId) {
        return toResponse(findByUserId(userId));
    }

    @Transactional
    public ProviderProfileResponse upsert(Long userId, ProviderProfileRequest request) {
        validateMarketId(request.marketId());

        ServiceProvider provider = serviceProviderRepository.findByUserId(userId)
                .orElseGet(() -> newProvider(userId));
        provider.setFirstName(request.firstName().trim());
        provider.setLastName(request.lastName().trim());
        provider.setNicNumber(optional(request.nicNumber()));
        provider.setPassportNumber(optional(request.passportNumber()));
        provider.setDateOfBirth(request.dateOfBirth());
        provider.setGender(request.gender().trim());
        provider.setProfilePhoto(optional(request.profilePhoto()));
        provider.setBio(optional(request.bio()));
        provider.setAvailabilityStatus(request.availabilityStatus());
        provider.setStatus("ACTIVE");
        provider = serviceProviderRepository.save(provider);
        replaceMarket(provider.getServiceProviderId(), request.marketId());
        return toResponse(provider);
    }

    @Transactional
    public ProviderProfileResponse updateMarket(Long userId, Long marketId) {
        ServiceProvider provider = findByUserId(userId);
        validateMarketId(marketId);
        replaceMarket(provider.getServiceProviderId(), marketId);
        return toResponse(provider);
    }

    public ServiceProvider findByUserId(Long userId) {
        return serviceProviderRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service provider profile has not been completed"
                ));
    }

    private ServiceProvider newProvider(Long userId) {
        ServiceProvider provider = new ServiceProvider();
        provider.setUserId(userId);
        provider.setVerificationStatus("NOT_SUBMITTED");
        provider.setAverageRating(BigDecimal.ZERO);
        provider.setTotalRatings(0);
        return provider;
    }

    private ProviderProfileResponse toResponse(ServiceProvider provider) {
        Long marketId = providerMarketRepository
                .findByServiceProviderIdAndStatus(
                        provider.getServiceProviderId(),
                        "ACTIVE"
                )
                .map(ServiceProviderMarket::getMarketId)
                .orElse(null);
        return new ProviderProfileResponse(
                provider.getServiceProviderId(),
                provider.getUserId(),
                provider.getFirstName(),
                provider.getLastName(),
                provider.getNicNumber(),
                provider.getPassportNumber(),
                provider.getDateOfBirth(),
                provider.getGender(),
                provider.getProfilePhoto(),
                provider.getBio(),
                provider.getVerificationStatus(),
                provider.getAvailabilityStatus(),
                provider.getStatus(),
                provider.getAverageRating(),
                provider.getTotalRatings(),
                marketId,
                marketId != null
        );
    }

    private void validateMarketId(Long marketId) {
        marketRepository.findById(marketId)
                .filter(market -> "ACTIVE".equals(market.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Market is invalid or inactive"
                ));
    }

    private void replaceMarket(Long providerId, Long marketId) {
        providerMarketRepository.deleteAllByServiceProviderId(providerId);
        providerMarketRepository.flush();
        ServiceProviderMarket assignment = new ServiceProviderMarket();
        assignment.setServiceProviderId(providerId);
        assignment.setMarketId(marketId);
        assignment.setStatus("ACTIVE");
        providerMarketRepository.save(assignment);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String optional(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
