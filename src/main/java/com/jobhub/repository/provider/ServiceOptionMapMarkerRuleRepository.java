package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceOptionMapMarkerRule;
import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface ServiceOptionMapMarkerRuleRepository extends JpaRepository<ServiceOptionMapMarkerRule, Long> {
    @EntityGraph(attributePaths = "conditions")
    List<ServiceOptionMapMarkerRule> findAllByServiceProviderTypeIdAndOptionIdOrderByMarkerRuleIdAsc(
            Long serviceTypeId, Long optionId);
    @EntityGraph(attributePaths = "conditions")
    List<ServiceOptionMapMarkerRule> findAllByServiceProviderTypeIdAndOptionIdAndStatus(
            Long serviceTypeId, Long optionId, String status);
}
