package com.jobhub.repository.job;

import com.jobhub.entity.job.JobProviderAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface JobProviderAssignmentRepository extends JpaRepository<JobProviderAssignment, Long> {
    List<JobProviderAssignment> findAllByJobIdOrderByJobProviderAssignmentIdAsc(Long jobId);

    @Query("""
            select count(a) from JobProviderAssignment a join Job j on j.jobId = a.jobId
            where a.serviceProviderId = :providerId and j.serviceOptionId = :optionId
              and j.jobStatus not in :excludedStatuses
              and j.startDatetime < :endTime and j.expectedEndDatetime > :startTime
            """)
    long countOverlapping(@Param("providerId") Long providerId,
                          @Param("optionId") Long optionId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime,
                          @Param("excludedStatuses") Collection<String> excludedStatuses);

    @Query("""
            select count(a) from JobProviderAssignment a join Job j on j.jobId = a.jobId
            where a.serviceProviderId = :providerId and j.serviceProviderTypeId = :serviceTypeId
              and j.jobStatus not in :excludedStatuses
              and j.startDatetime < :endTime and j.expectedEndDatetime > :startTime
            """)
    long countProviderServiceOverlapping(@Param("providerId") Long providerId,
                                         @Param("serviceTypeId") Long serviceTypeId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime,
                                         @Param("excludedStatuses") Collection<String> excludedStatuses);
}
