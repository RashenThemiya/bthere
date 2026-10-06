package com.jobhub.repository.job;

import com.jobhub.entity.job.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long> {
    long countByJobStatus(String status);
    List<Job> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @Query("""
            select count(j) from Job j
            where j.assignedServiceProviderId = :providerId and j.serviceOptionId = :optionId
              and j.jobStatus not in :excludedStatuses
              and j.startDatetime < :endTime and j.expectedEndDatetime > :startTime
            """)
    long countPrimaryOverlapping(@Param("providerId") Long providerId,
                                 @Param("optionId") Long optionId,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime,
                                 @Param("excludedStatuses") Collection<String> excludedStatuses);

    @Query("""
            select count(j) from Job j
            where j.assignedServiceProviderId = :providerId
              and j.serviceProviderTypeId = :serviceTypeId
              and j.jobStatus not in :excludedStatuses
              and j.startDatetime < :endTime and j.expectedEndDatetime > :startTime
            """)
    long countProviderServiceOverlapping(@Param("providerId") Long providerId,
                                         @Param("serviceTypeId") Long serviceTypeId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime,
                                         @Param("excludedStatuses") Collection<String> excludedStatuses);

    @Query("""
            select count(j) from Job j
            where j.assignedServiceProviderId = :providerId and j.serviceOptionId = :optionId
              and j.providerLocationId = :locationId and j.jobStatus not in :excludedStatuses
              and j.startDatetime < :endTime and j.expectedEndDatetime > :startTime
            """)
    long countLocationOverlapping(@Param("providerId") Long providerId,
                                  @Param("optionId") Long optionId,
                                  @Param("locationId") Long locationId,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime,
                                  @Param("excludedStatuses") Collection<String> excludedStatuses);
}
