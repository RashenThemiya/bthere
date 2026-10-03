package com.jobhub.config;

import com.jobhub.entity.market.Market;
import com.jobhub.repository.market.MarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.bootstrap.default-market.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DefaultMarketBootstrap implements ApplicationRunner {

    private final MarketRepository marketRepository;

    @Value("${app.bootstrap.default-market.name}")
    private String name;

    @Value("${app.bootstrap.default-market.country-code}")
    private String countryCode;

    @Value("${app.bootstrap.default-market.currency}")
    private String currency;

    @Value("${app.bootstrap.default-market.timezone}")
    private String timezone;

    @Value("${app.bootstrap.default-market.locale}")
    private String locale;

    @Value("${app.bootstrap.default-market.phone-country-code}")
    private String phoneCountryCode;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (marketRepository.count() > 0) {
            return;
        }

        Market market = new Market();
        market.setName(name);
        market.setCountryCode(countryCode.toUpperCase());
        market.setDefaultCurrency(currency.toUpperCase());
        market.setTimezone(timezone);
        market.setLocale(locale);
        market.setPhoneCountryCode(phoneCountryCode);
        market.setStatus("ACTIVE");
        marketRepository.save(market);
    }
}
