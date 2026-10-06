package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceOptionRepository extends JpaRepository<ServiceOption, Long> {
    List<ServiceOption> findAllByServiceProviderTypeIdOrderByDisplayOrderAscNameAsc(Long typeId);
    List<ServiceOption> findAllByServiceProviderTypeIdAndStatusOrderByDisplayOrderAscNameAsc(
            Long typeId, String status);
    List<ServiceOption> findAllByOptionIdInAndServiceProviderTypeIdAndStatus(
            Collection<Long> ids, Long typeId, String status);
    boolean existsByServiceProviderTypeIdAndCodeIgnoreCase(Long typeId, String code);
    Optional<ServiceOption> findByOptionIdAndServiceProviderTypeId(Long optionId, Long typeId);
}
