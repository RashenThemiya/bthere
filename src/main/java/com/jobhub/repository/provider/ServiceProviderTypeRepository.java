package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ServiceProviderTypeRepository extends JpaRepository<ServiceProviderType, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from ServiceProviderType s where s.serviceProviderTypeId=:id")
    java.util.Optional<ServiceProviderType> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByNameIgnoreCase(String name);

    List<ServiceProviderType> findAllByStatusOrderByNameAsc(String status);

    List<ServiceProviderType> findAllByOrderByNameAsc();

    List<ServiceProviderType> findAllByServiceProviderTypeIdInAndStatus(
            Collection<Long> ids,
            String status
    );

    long countByStatus(String status);
}
