package com.jobhub.repository.market;

import com.jobhub.entity.market.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MarketRepository extends JpaRepository<Market, Long> {

    boolean existsByCountryCode(String countryCode);

    boolean existsByCountryCodeAndMarketIdNot(String countryCode, Long marketId);

    List<Market> findAllByStatusOrderByNameAsc(String status);

    List<Market> findAllByOrderByNameAsc();

    List<Market> findAllByMarketIdInAndStatus(Collection<Long> ids, String status);
}
