package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceProviderServiceArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceProviderServiceAreaRepository
        extends JpaRepository<ServiceProviderServiceArea, Long> {

    List<ServiceProviderServiceArea> findAllByAssignmentIdOrderByLocationNameAsc(Long assignmentId);

    List<ServiceProviderServiceArea> findAllByAssignmentIdAndOptionIdAndDeliveryModeOrderByLocationNameAsc(
            Long assignmentId, Long optionId, String deliveryMode);

    boolean existsByAssignmentIdAndOptionIdAndDeliveryMode(
            Long assignmentId, Long optionId, String deliveryMode);

    void deleteAllByAssignmentId(Long assignmentId);

    void deleteAllByAssignmentIdAndOptionIdAndDeliveryMode(
            Long assignmentId, Long optionId, String deliveryMode);
}
