package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceProviderAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ServiceProviderAvailabilityRepository
        extends JpaRepository<ServiceProviderAvailability, Long> {

    List<ServiceProviderAvailability> findAllByAssignmentIdAndScheduleTypeOrderByDayOfWeekAscStartTimeAsc(
            Long assignmentId, String scheduleType);

    List<ServiceProviderAvailability> findAllByAssignmentIdAndScheduleTypeOrderByAvailableDateAsc(
            Long assignmentId, String scheduleType);

    boolean existsByAssignmentIdAndScheduleTypeAndAvailableDate(
            Long assignmentId, String scheduleType, LocalDate availableDate);

    void deleteAllByAssignmentIdAndScheduleType(Long assignmentId, String scheduleType);
}
