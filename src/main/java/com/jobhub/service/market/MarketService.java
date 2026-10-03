package com.jobhub.service.market;

import com.jobhub.dto.market.CreateMarketRequest;
import com.jobhub.dto.market.MarketResponse;
import com.jobhub.entity.market.Market;
import com.jobhub.exception.ConflictException;
import com.jobhub.repository.market.MarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;

@Service
@RequiredArgsConstructor
public class MarketService {

    private final MarketRepository marketRepository;

    @Transactional
    public MarketResponse create(CreateMarketRequest request) {
        String countryCode = request.countryCode().trim().toUpperCase();
        String currencyCode = request.defaultCurrency().trim().toUpperCase();

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
        market.setPhoneCountryCode(request.phoneCountryCode().trim());
        market.setStatus("ACTIVE");

        return toResponse(marketRepository.saveAndFlush(market));
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
