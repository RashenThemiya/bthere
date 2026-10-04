package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceTypeDocumentRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ServiceTypeDocumentRequirementRepository
        extends JpaRepository<ServiceTypeDocumentRequirement, Long> {

    List<ServiceTypeDocumentRequirement> findAllByServiceProviderTypeId(
            Long serviceProviderTypeId
    );

    List<ServiceTypeDocumentRequirement> findAllByServiceProviderTypeIdIn(
            Collection<Long> serviceProviderTypeIds
    );

    void deleteAllByServiceProviderTypeId(Long serviceProviderTypeId);
}
