package com.jobhub.repository.market;

import com.jobhub.entity.market.MarketServiceOffering;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MarketServiceOfferingRepository extends JpaRepository<MarketServiceOffering, Long> {
    boolean existsByMarketIdAndServiceProviderTypeId(Long marketId, Long typeId);
    Page<MarketServiceOffering> findAllByMarketId(Long marketId, Pageable pageable);
    Optional<MarketServiceOffering> findByMarketIdAndServiceProviderTypeId(Long marketId, Long typeId);
}
