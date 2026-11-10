package com.jobhub.repository.job;

import com.jobhub.entity.job.JobCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobCancellationRepository extends JpaRepository<JobCancellation, Long> {
    Optional<JobCancellation> findByJobId(Long jobId);
}
