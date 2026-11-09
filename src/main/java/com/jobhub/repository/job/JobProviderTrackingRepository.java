package com.jobhub.repository.job;

import com.jobhub.entity.job.JobProviderTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobProviderTrackingRepository extends JpaRepository<JobProviderTracking, Long> {
    Optional<JobProviderTracking> findByJobIdAndServiceProviderId(Long jobId, Long serviceProviderId);
    List<JobProviderTracking> findAllByJobId(Long jobId);
    @Query("select t from JobProviderTracking t, Job j where j.jobId = t.jobId and t.serviceProviderId = :providerId and j.jobStatus in :statuses")
    List<JobProviderTracking> findOpenForProvider(@Param("providerId") Long providerId, @Param("statuses") Collection<String> statuses);
}
