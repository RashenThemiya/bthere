package com.jobhub.repository.market;

import com.jobhub.entity.market.MarketBanner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketBannerRepository extends JpaRepository<MarketBanner, Long> {
    List<MarketBanner> findByMarketIdOrderBySortOrderAscBannerIdAsc(Long marketId);

    List<MarketBanner> findByMarketIdAndStatusOrderBySortOrderAscBannerIdAsc(Long marketId, String status);

    long countByMarketId(Long marketId);
}
