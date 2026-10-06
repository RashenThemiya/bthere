package com.jobhub.repository.job;

import com.jobhub.entity.job.JobRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobRateRepository extends JpaRepository<JobRate, Long> {
    Page<JobRate> findAllByMarketIdAndServiceProviderTypeId(
            Long marketId, Long serviceTypeId, Pageable pageable);

    List<JobRate> findAllByMarketIdAndServiceProviderTypeIdAndServiceOptionIdAndStatus(
            Long marketId, Long serviceTypeId, Long optionId, String status);
    long countByStatus(String status);
}
