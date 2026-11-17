package com.jobhub.repository.job;

import com.jobhub.entity.job.NoShowClaim;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NoShowClaimRepository extends JpaRepository<NoShowClaim, Long> {
    Optional<NoShowClaim> findByJobId(Long jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from NoShowClaim c where c.claimId = :id")
    Optional<NoShowClaim> lockById(@Param("id") Long id);

    @Query("select c from NoShowClaim c where (:status is null or c.status = :status)"
            + " and (:providerId is null or c.serviceProviderId = :providerId)")
    Page<NoShowClaim> search(@Param("status") String status, @Param("providerId") Long providerId, Pageable pageable);
}
