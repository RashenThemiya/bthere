package com.jobhub.repository.market;

import com.jobhub.entity.market.Market;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketRepository extends JpaRepository<Market, Long> {

    boolean existsByCountryCode(String countryCode);
}
