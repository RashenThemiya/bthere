package com.jobhub.repository.job;

import com.jobhub.entity.job.ProviderPenalty;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProviderPenaltyRepository extends JpaRepository<ProviderPenalty, Long> {
    Optional<ProviderPenalty> findByJobId(Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProviderPenalty p where p.penaltyId = :id")
    Optional<ProviderPenalty> lockById(@Param("id") Long id);

    @Query("select p.penaltyId from ProviderPenalty p where p.providerUserId = :userId and p.currencyCode = :currency"
            + " and p.status = 'OUTSTANDING' order by p.penaltyId")
    List<Long> outstandingIds(@Param("userId") Long providerUserId, @Param("currency") String currency);

    @Query("select coalesce(sum(p.penaltyAmount - p.collectedAmount), 0) from ProviderPenalty p"
            + " where p.providerUserId = :userId and p.currencyCode = :currency and p.status = 'OUTSTANDING'")
    BigDecimal outstandingTotal(@Param("userId") Long providerUserId, @Param("currency") String currency);

    List<ProviderPenalty> findAllByProviderUserIdOrderByPenaltyIdDesc(Long providerUserId);

    @Query("select p from ProviderPenalty p where (:providerId is null or p.serviceProviderId = :providerId)"
            + " and (:status is null or p.status = :status)")
    Page<ProviderPenalty> search(@Param("providerId") Long serviceProviderId, @Param("status") String status,
                                 Pageable pageable);
}
