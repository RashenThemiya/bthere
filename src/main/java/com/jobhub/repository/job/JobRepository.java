package com.jobhub.repository.job;

import com.jobhub.entity.job.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.jobId = :id")
    java.util.Optional<Job> lockPaymentJob(@Param("id") Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select j from Job j where j.jobStatus not in :excluded
            and j.startDatetime < :endTime and j.expectedEndDatetime > :startTime
            and (j.assignedServiceProviderId = :providerId or exists
                (select a.jobProviderAssignmentId from JobProviderAssignment a
                 where a.jobId = j.jobId and a.serviceProviderId = :providerId
                 and a.status = 'ASSIGNED'))
            """)
    List<Job> findLockedReservedOverlaps(@Param("providerId") Long providerId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("excluded") Collection<String> excluded);
    @Query("""
            select j from Job j where j.jobStatus not in :excluded
            and j.startDatetime < :endTime and j.expectedEndDatetime > :startTime
            and (j.assignedServiceProviderId = :providerId or exists
                (select a.jobProviderAssignmentId from JobProviderAssignment a
                 where a.jobId = j.jobId and a.serviceProviderId = :providerId
                 and a.status = 'ASSIGNED'))
            """)
    List<Job> findReservedOverlaps(@Param("providerId") Long providerId,
            @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime,
            @Param("excluded") Collection<String> excluded);
    long countByJobStatus(String status);
    List<Job> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);
    long countBySchedulingModel(String schedulingModel);
    long countByFulfillmentModel(String fulfillmentModel);

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
