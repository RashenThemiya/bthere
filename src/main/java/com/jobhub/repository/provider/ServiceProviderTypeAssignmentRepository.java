package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceProviderTypeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceProviderTypeAssignmentRepository
        extends JpaRepository<ServiceProviderTypeAssignment, Long> {

    List<ServiceProviderTypeAssignment> findAllByServiceProviderIdAndStatus(
            Long serviceProviderId,
            String status
    );

    void deleteAllByServiceProviderId(Long serviceProviderId);

    List<ServiceProviderTypeAssignment> findAllByServiceProviderId(Long serviceProviderId);

    List<ServiceProviderTypeAssignment> findAllByServiceProviderTypeId(
            Long serviceProviderTypeId
    );

    Optional<ServiceProviderTypeAssignment> findByServiceProviderIdAndServiceProviderTypeId(
            Long serviceProviderId,
            Long serviceProviderTypeId
    );

    Optional<ServiceProviderTypeAssignment> findByAssignmentIdAndServiceProviderId(
            Long assignmentId,
            Long serviceProviderId
    );

    long countByVerificationStatus(String status);
}
