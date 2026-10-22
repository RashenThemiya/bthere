package com.jobhub.repository.job;

import com.jobhub.entity.job.JobRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobRateRepository extends JpaRepository<JobRate, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from JobRate r where r.rateId = :rateId")
    java.util.Optional<JobRate> findLockedById(@org.springframework.data.repository.query.Param("rateId") Long rateId);
    Page<JobRate> findAllByMarketIdAndServiceProviderTypeId(
            Long marketId, Long serviceTypeId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            select r from JobRate r where r.marketId = :marketId and
              ((r.serviceOptionId is null and r.serviceProviderTypeId = :serviceTypeId)
               or exists (select o.optionId from ServiceOption o join o.serviceTypes l
                 where o.optionId = r.serviceOptionId
                   and o.serviceProviderTypeId = r.serviceProviderTypeId
                   and l.serviceProviderTypeId = :serviceTypeId))
            """)
    Page<JobRate> findSharedRates(@org.springframework.data.repository.query.Param("marketId") Long marketId,
            @org.springframework.data.repository.query.Param("serviceTypeId") Long serviceTypeId, Pageable pageable);

    List<JobRate> findAllByMarketIdAndServiceProviderTypeIdAndServiceOptionIdAndStatus(
            Long marketId, Long serviceTypeId, Long optionId, String status);
    long countByStatus(String status);
}
