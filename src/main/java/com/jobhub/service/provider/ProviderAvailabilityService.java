package com.jobhub.service.provider;

import com.jobhub.dto.provider.*;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProviderAvailabilityService {

    private static final String WEEKLY = "WEEKLY";
    private static final String UNAVAILABLE_DATE = "UNAVAILABLE_DATE";

    private final ProviderProfileService profileService;
    private final ServiceProviderTypeAssignmentRepository assignmentRepository;
    private final ServiceProviderTypeRepository typeRepository;
    private final ServiceProviderAvailabilityRepository availabilityRepository;
    private final ServiceProviderServiceAreaRepository serviceAreaRepository;
    private final ServiceProviderMarketRepository marketRepository;
    private final ServiceOptionRepository optionRepository;
    private final ProviderServiceOptionRepository providerOptionRepository;
    private final ProviderServiceApprovalService approvalService;

    @Transactional(readOnly = true)
    public ProviderScheduleResponse getSchedule(Long userId, Long assignmentId) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        requireAvailabilityEnabled(assignment);
        return scheduleResponse(assignment);
    }

    @Transactional
    public ProviderScheduleResponse replaceSchedule(
            Long userId, Long assignmentId, ProviderScheduleRequest request) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        requireAvailabilityEnabled(assignment);
        validateSchedule(request);

        availabilityRepository.deleteAllByAssignmentIdAndScheduleType(assignmentId, WEEKLY);
        assignment.setAvailableAnyDay(request.availableAnyDay());
        assignment.setAvailableAnyTime(request.availableAnyTime());
        assignmentRepository.save(assignment);

        Set<String> duplicateCheck = new HashSet<>();
        for (ProviderScheduleRequest.WeeklySlot slot : request.weeklySlots()) {
            String key = slot.dayOfWeek() + ":" + slot.startTime() + ":" + slot.endTime();
            if (!duplicateCheck.add(key)) {
                throw new IllegalArgumentException("Weekly availability contains duplicate slots");
            }
            ServiceProviderAvailability item = new ServiceProviderAvailability();
            item.setServiceProviderId(assignment.getServiceProviderId());
            item.setAssignmentId(assignmentId);
            item.setScheduleType(WEEKLY);
            item.setDayOfWeek(slot.dayOfWeek());
            item.setStartTime(request.availableAnyTime() ? null : slot.startTime());
            item.setEndTime(request.availableAnyTime() ? null : slot.endTime());
            item.setAllDay(request.availableAnyTime());
            item.setStatus("ACTIVE");
            availabilityRepository.save(item);
        }
        approvalService.refreshProviderApprovals(assignment.getServiceProviderId());
        return scheduleResponse(assignment);
    }

    @Transactional(readOnly = true)
    public List<ProviderUnavailableDateResponse> getUnavailableDates(Long userId, Long assignmentId) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        requireAvailabilityEnabled(assignment);
        return availabilityRepository
                .findAllByAssignmentIdAndScheduleTypeOrderByAvailableDateAsc(
                        assignmentId, UNAVAILABLE_DATE)
                .stream().map(this::unavailableResponse).toList();
    }

    @Transactional
    public ProviderUnavailableDateResponse addUnavailableDate(
            Long userId, Long assignmentId, ProviderUnavailableDateRequest request) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        requireAvailabilityEnabled(assignment);
        if (request.date().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Unavailable date cannot be in the past");
        }
        if (availabilityRepository.existsByAssignmentIdAndScheduleTypeAndAvailableDate(
                assignmentId, UNAVAILABLE_DATE, request.date())) {
            throw new IllegalArgumentException("This date is already marked unavailable");
        }
        ServiceProviderAvailability item = new ServiceProviderAvailability();
        item.setServiceProviderId(assignment.getServiceProviderId());
        item.setAssignmentId(assignmentId);
        item.setScheduleType(UNAVAILABLE_DATE);
        item.setAvailableDate(request.date());
        item.setAllDay(true);
        item.setReason(trim(request.reason()));
        item.setStatus("UNAVAILABLE");
        return unavailableResponse(availabilityRepository.save(item));
    }

    @Transactional
    public void deleteUnavailableDate(Long userId, Long assignmentId, Long unavailableDateId) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        requireAvailabilityEnabled(assignment);
        ServiceProviderAvailability item = availabilityRepository.findById(unavailableDateId)
                .filter(value -> assignmentId.equals(value.getAssignmentId())
                        && UNAVAILABLE_DATE.equals(value.getScheduleType()))
                .orElseThrow(() -> new ResourceNotFoundException("Unavailable date not found"));
        availabilityRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public List<ProviderServiceAreaResponse> getServiceAreas(
            Long userId, Long assignmentId, Long optionId, String deliveryMode) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        String normalizedMode = requireLocationConfiguration(assignment, optionId, deliveryMode);
        return serviceAreaRepository
                .findAllByAssignmentIdAndOptionIdAndDeliveryModeOrderByLocationNameAsc(
                        assignmentId, optionId, normalizedMode)
                .stream().map(this::areaResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ProviderServiceAreaResponse> getLegacyServiceAreas(
            Long userId, Long assignmentId) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        LegacyLocation target = legacyLocation(assignment, false);
        if (target == null) return List.of();
        return serviceAreaRepository
                .findAllByAssignmentIdAndOptionIdAndDeliveryModeOrderByLocationNameAsc(
                        assignmentId, target.optionId(), target.deliveryMode())
                .stream().map(this::areaResponse).toList();
    }

    @Transactional
    public List<ProviderServiceAreaResponse> replaceLegacyServiceAreas(
            Long userId, Long assignmentId, List<ProviderServiceAreaRequest> requests) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        LegacyLocation target = legacyLocation(assignment, true);
        return replaceServiceAreas(userId, assignmentId, target.optionId(),
                target.deliveryMode(), requests);
    }

    @Transactional
    public List<ProviderServiceAreaResponse> replaceServiceAreas(
            Long userId, Long assignmentId, Long optionId, String deliveryMode,
            List<ProviderServiceAreaRequest> requests) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        String normalizedMode = requireLocationConfiguration(assignment, optionId, deliveryMode);
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("At least one service location is required");
        }
        Long marketId = marketRepository.findByServiceProviderIdAndStatus(
                        assignment.getServiceProviderId(), "ACTIVE")
                .orElseThrow(() -> new IllegalArgumentException(
                        "Select a market before adding service locations"))
                .getMarketId();
        Set<String> names = new HashSet<>();
        for (ProviderServiceAreaRequest request : requests) {
            if (!names.add(request.locationName().trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Service locations cannot contain duplicate names");
            }
            validateRadius(normalizedMode, request.radiusKm());
        }
        serviceAreaRepository.deleteAllByAssignmentIdAndOptionIdAndDeliveryMode(
                assignmentId, optionId, normalizedMode);
        for (ProviderServiceAreaRequest request : requests) {
            ServiceProviderServiceArea item = new ServiceProviderServiceArea();
            item.setMarketId(marketId);
            item.setServiceProviderId(assignment.getServiceProviderId());
            item.setAssignmentId(assignmentId);
            item.setOptionId(optionId);
            item.setDeliveryMode(normalizedMode);
            item.setLocationName(request.locationName().trim());
            item.setCountry(trim(request.country()));
            item.setProvince(trim(request.province()));
            item.setDistrict(trim(request.district()));
            item.setCity(trim(request.city()));
            item.setLatitude(request.latitude());
            item.setLongitude(request.longitude());
            item.setRadiusKm(request.radiusKm());
            item.setCapacity(request.capacity());
            item.setStatus("ACTIVE");
            serviceAreaRepository.save(item);
        }
        approvalService.refreshProviderApprovals(assignment.getServiceProviderId());
        return serviceAreaRepository
                .findAllByAssignmentIdAndOptionIdAndDeliveryModeOrderByLocationNameAsc(
                        assignmentId, optionId, normalizedMode)
                .stream().map(this::areaResponse).toList();
    }

    private void validateRadius(String deliveryMode, java.math.BigDecimal radiusKm) {
        if ("PROVIDER_TO_CUSTOMER".equals(deliveryMode) && radiusKm == null) {
            throw new IllegalArgumentException(
                    "radiusKm is required when the provider travels to the customer");
        }
        if ("CUSTOMER_TO_PROVIDER".equals(deliveryMode) && radiusKm != null) {
            throw new IllegalArgumentException(
                    "radiusKm must be empty for a fixed provider location");
        }
    }

    private void validateSchedule(ProviderScheduleRequest request) {
        if (request.availableAnyDay() && !request.weeklySlots().isEmpty()) {
            throw new IllegalArgumentException(
                    "Weekly slots must be empty when availableAnyDay is true");
        }
        if (!request.availableAnyDay() && request.weeklySlots().isEmpty()) {
            throw new IllegalArgumentException(
                    "Select at least one weekly slot or enable availableAnyDay");
        }
        if (!request.availableAnyTime()) {
            request.weeklySlots().forEach(slot -> {
                if (slot.startTime() == null || slot.endTime() == null
                        || !slot.startTime().isBefore(slot.endTime())) {
                    throw new IllegalArgumentException(
                            "Every weekly slot requires a start time before its end time");
                }
            });
        }
    }

    private ServiceProviderTypeAssignment ownedAssignment(Long userId, Long assignmentId) {
        ServiceProvider provider = profileService.findByUserId(userId);
        return assignmentRepository.findByAssignmentIdAndServiceProviderId(
                        assignmentId, provider.getServiceProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider service not found"));
    }

    private void requireAvailabilityEnabled(ServiceProviderTypeAssignment assignment) {
        ServiceProviderType type = requireType(assignment);
        if (!type.isAvailabilityEnabled()) {
            throw new IllegalArgumentException("Availability is disabled for this service");
        }
    }

    private String requireLocationConfiguration(
            ServiceProviderTypeAssignment assignment, Long optionId, String deliveryMode) {
        ServiceOption option = optionRepository
                .findByOptionIdAndServiceProviderTypeId(
                        optionId, assignment.getServiceProviderTypeId())
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Active service option not found"));
        boolean selected = providerOptionRepository.findAllByAssignmentId(assignment.getAssignmentId())
                .stream().anyMatch(item -> optionId.equals(item.getOptionId()));
        if (!selected) {
            throw new IllegalArgumentException("Select this service option before adding locations");
        }
        if (!option.isLocationEnabled()) {
            throw new IllegalArgumentException("Locations are disabled for this service option");
        }
        String mode = deliveryMode.trim().toUpperCase(Locale.ROOT);
        if ("ONLINE".equals(mode)) {
            throw new IllegalArgumentException("Online delivery does not use a physical location");
        }
        if (!option.getDeliveryModes().contains(mode)) {
            throw new IllegalArgumentException("Delivery mode is not enabled for this service option");
        }
        return mode;
    }

    private LegacyLocation legacyLocation(
            ServiceProviderTypeAssignment assignment, boolean enableWhenRequired) {
        ServiceProviderType type = requireType(assignment);
        if (!type.isServiceAreasEnabled()) {
            throw new IllegalArgumentException("Legacy service locations are disabled");
        }
        Set<Long> selectedIds = providerOptionRepository
                .findAllByAssignmentId(assignment.getAssignmentId()).stream()
                .map(ProviderServiceOption::getOptionId)
                .collect(java.util.stream.Collectors.toSet());
        ServiceOption option = optionRepository
                .findAllByOptionIdInAndServiceProviderTypeIdAndStatus(
                        selectedIds, assignment.getServiceProviderTypeId(), "ACTIVE")
                .stream().sorted(Comparator.comparing(ServiceOption::getDisplayOrder)
                        .thenComparing(ServiceOption::getOptionId)).findFirst()
                .orElse(null);
        if (option == null) {
            if (!enableWhenRequired) return null;
            throw new IllegalArgumentException("Select a service option before adding locations");
        }
        String mode = option.getDeliveryModes().contains("PROVIDER_TO_CUSTOMER")
                ? "PROVIDER_TO_CUSTOMER"
                : option.getDeliveryModes().stream().filter(value -> !"ONLINE".equals(value))
                .findFirst().orElse("PROVIDER_TO_CUSTOMER");
        if (enableWhenRequired && !option.isLocationEnabled()) {
            option.setLocationEnabled(true);
            Set<String> modes = new LinkedHashSet<>(option.getDeliveryModes());
            modes.add(mode);
            option.setDeliveryModes(modes);
            optionRepository.save(option);
        }
        return new LegacyLocation(option.getOptionId(), mode);
    }

    private ServiceProviderType requireType(ServiceProviderTypeAssignment assignment) {
        return typeRepository.findById(assignment.getServiceProviderTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Service type not found"));
    }

    private ProviderScheduleResponse scheduleResponse(ServiceProviderTypeAssignment assignment) {
        List<ProviderScheduleResponse.WeeklySlot> slots = availabilityRepository
                .findAllByAssignmentIdAndScheduleTypeOrderByDayOfWeekAscStartTimeAsc(
                        assignment.getAssignmentId(), WEEKLY)
                .stream().map(item -> new ProviderScheduleResponse.WeeklySlot(
                        item.getAvailabilityId(), item.getDayOfWeek(),
                        item.getStartTime(), item.getEndTime())).toList();
        return new ProviderScheduleResponse(
                assignment.isAvailableAnyDay(), assignment.isAvailableAnyTime(), slots);
    }

    private ProviderUnavailableDateResponse unavailableResponse(ServiceProviderAvailability item) {
        return new ProviderUnavailableDateResponse(
                item.getAvailabilityId(), item.getAvailableDate(), item.getReason());
    }

    private ProviderServiceAreaResponse areaResponse(ServiceProviderServiceArea item) {
        return new ProviderServiceAreaResponse(
                item.getServiceAreaId(), item.getOptionId(), item.getDeliveryMode(),
                item.getLocationName(), item.getCountry(),
                item.getProvince(), item.getDistrict(), item.getCity(), item.getLatitude(),
                item.getLongitude(), item.getRadiusKm(), item.getCapacity(), item.getStatus());
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record LegacyLocation(Long optionId, String deliveryMode) {}
}
