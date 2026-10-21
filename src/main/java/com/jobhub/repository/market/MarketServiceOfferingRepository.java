package com.jobhub.repository.market;

import com.jobhub.entity.market.MarketServiceOffering;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MarketServiceOfferingRepository extends JpaRepository<MarketServiceOffering, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from MarketServiceOffering o where o.offeringId = :id")
    Optional<MarketServiceOffering> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    boolean existsByMarketIdAndServiceProviderTypeId(Long marketId, Long typeId);
    Page<MarketServiceOffering> findAllByMarketId(Long marketId, Pageable pageable);
    Optional<MarketServiceOffering> findByMarketIdAndServiceProviderTypeId(Long marketId, Long typeId);
    long countByStatus(String status);
}
