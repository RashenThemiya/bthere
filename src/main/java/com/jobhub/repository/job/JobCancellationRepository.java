package com.jobhub.repository.job;

import com.jobhub.entity.job.JobCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobCancellationRepository extends JpaRepository<JobCancellation, Long> {
    Optional<JobCancellation> findByJobId(Long jobId);

    @org.springframework.data.jpa.repository.Query("select count(c) from JobCancellation c where c.cancelledBy = 'PROVIDER'"
            + " and c.cancelledByUserId = :userId and c.createdAt >= :since and c.penaltyWaived = false")
    long countProviderCancellationsSince(@org.springframework.data.repository.query.Param("userId") Long providerUserId,
                                         @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
}
