package com.jobhub.service.market;

import com.jobhub.dto.market.CreateMarketRequest;
import com.jobhub.dto.market.MarketResponse;
import com.jobhub.entity.market.Market;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.market.MarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MarketService {

    private final MarketRepository marketRepository;

    @Transactional
    public MarketResponse create(CreateMarketRequest request) {
        String countryCode = request.countryCode().trim().toUpperCase();
        String currencyCode = request.currencyCode().trim().toUpperCase();

        if (marketRepository.existsByCountryCode(countryCode)) {
            throw new ConflictException("A market for this country already exists");
        }

        validateCurrency(currencyCode);
        validateTimezone(request.timezone());

        Market market = new Market();
        market.setName(request.name().trim());
        market.setCountryCode(countryCode);
        market.setDefaultCurrency(currencyCode);
        market.setTimezone(request.timezone().trim());
        market.setLocale(request.locale().trim());
        market.setPhoneCountryCode(request.phoneCode().trim());
        market.setStatus("ACTIVE");

        return toResponse(marketRepository.saveAndFlush(market));
    }

    @Transactional(readOnly = true)
    public List<MarketResponse> listActive() {
        return marketRepository.findAllByStatusOrderByNameAsc("ACTIVE")
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MarketResponse> listAll() {
        return marketRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MarketResponse update(Long marketId, CreateMarketRequest request) {
        Market market = requireMarket(marketId);
        String countryCode = request.countryCode().trim().toUpperCase();
        String currencyCode = request.currencyCode().trim().toUpperCase();
        if (marketRepository.existsByCountryCodeAndMarketIdNot(countryCode, marketId)) {
            throw new ConflictException("A market for this country already exists");
        }
        validateCurrency(currencyCode);
        validateTimezone(request.timezone());
        market.setName(request.name().trim());
        market.setCountryCode(countryCode);
        market.setDefaultCurrency(currencyCode);
        market.setTimezone(request.timezone().trim());
        market.setLocale(request.locale().trim());
        market.setPhoneCountryCode(request.phoneCode().trim());
        return toResponse(marketRepository.save(market));
    }

    @Transactional
    public MarketResponse updateStatus(Long marketId, String requestedStatus) {
        String status = requestedStatus.trim().toUpperCase();
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }
        Market market = requireMarket(marketId);
        market.setStatus(status);
        return toResponse(marketRepository.save(market));
    }

    private Market requireMarket(Long marketId) {
        return marketRepository.findById(marketId)
                .orElseThrow(() -> new ResourceNotFoundException("Market was not found"));
    }

    private void validateCurrency(String currencyCode) {
        try {
            Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported ISO currency code");
        }
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone.trim());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Unsupported IANA timezone");
        }
    }

    private MarketResponse toResponse(Market market) {
        return new MarketResponse(
                market.getMarketId(),
                market.getName(),
                market.getCountryCode(),
                market.getDefaultCurrency(),
                market.getTimezone(),
                market.getLocale(),
                market.getPhoneCountryCode(),
                market.getStatus(),
                market.getCreatedAt(),
                market.getUpdatedAt()
        );
    }
}
