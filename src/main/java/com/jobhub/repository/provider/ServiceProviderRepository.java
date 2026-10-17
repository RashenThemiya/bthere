package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceProviderRepository extends JpaRepository<ServiceProvider, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from ServiceProvider p where p.serviceProviderId = :id")
    Optional<ServiceProvider> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<ServiceProvider> findByUserId(Long userId);

    long countByVerificationStatus(String status);
}
