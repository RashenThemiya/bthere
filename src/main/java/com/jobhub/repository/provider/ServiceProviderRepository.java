package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceProviderRepository extends JpaRepository<ServiceProvider, Long> {
    @org.springframework.data.jpa.repository.Query("select p from ServiceProvider p where exists (select a.assignmentId from ServiceProviderTypeAssignment a where a.serviceProviderId=p.serviceProviderId and (:status='ALL' or a.verificationStatus=:status)) order by p.serviceProviderId asc")
    org.springframework.data.domain.Page<ServiceProvider> findApprovalProviders(
            @org.springframework.data.repository.query.Param("status") String status,
            org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from ServiceProvider p where p.serviceProviderId = :id")
    Optional<ServiceProvider> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<ServiceProvider> findByUserId(Long userId);

    long countByVerificationStatus(String status);
}
