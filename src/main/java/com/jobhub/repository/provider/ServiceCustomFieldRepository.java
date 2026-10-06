package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceCustomField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceCustomFieldRepository extends JpaRepository<ServiceCustomField, Long> {
    List<ServiceCustomField> findAllByServiceProviderTypeIdOrderByDisplayOrderAscLabelAsc(Long typeId);
    List<ServiceCustomField> findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(
            Long typeId, String scope, String status);
    List<ServiceCustomField> findAllByFieldIdIn(Collection<Long> ids);
    Optional<ServiceCustomField> findByFieldIdAndServiceProviderTypeId(Long fieldId, Long typeId);
    boolean existsByServiceProviderTypeIdAndScopeAndOptionIdAndCodeIgnoreCase(
            Long typeId, String scope, Long optionId, String code);
    long countByScopeAndStatus(String scope, String status);
}
