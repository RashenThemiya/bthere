package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ServiceProviderTypeRepository extends JpaRepository<ServiceProviderType, Long> {

    boolean existsByNameIgnoreCase(String name);

    List<ServiceProviderType> findAllByStatusOrderByNameAsc(String status);

    List<ServiceProviderType> findAllByServiceProviderTypeIdInAndStatus(
            Collection<Long> ids,
            String status
    );

    long countByStatus(String status);
}
