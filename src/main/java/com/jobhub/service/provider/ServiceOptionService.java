package com.jobhub.service.provider;

import com.jobhub.dto.provider.*;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ServiceOptionService {

    private static final Set<String> DELIVERY_MODES = Set.of(
            "PROVIDER_TO_CUSTOMER", "CUSTOMER_TO_PROVIDER", "ONLINE");
    private static final Set<String> SCHEDULING_MODELS = Set.of("TIME_BASED", "ROUTE_BASED");
    private static final Set<String> FULFILLMENT_MODELS = Set.of(
            "ONE_TO_ONE", "MANY_CUSTOMERS_ONE_PROVIDER", "ONE_CUSTOMER_MANY_PROVIDERS");
    private static final Set<String> BOOKING_MODES = Set.of("ONE_AT_A_TIME", "MANY_AT_A_TIME");

    private static final Set<String> LANGUAGES = Set.of("SINHALA", "ENGLISH", "TAMIL");

    private final ServiceProviderTypeRepository typeRepository;
    private final ServiceOptionRepository optionRepository;
    private final ProviderServiceOptionRepository providerOptionRepository;
    private final ProviderServiceLanguageRepository languageRepository;
    private final ServiceProviderTypeAssignmentRepository assignmentRepository;
    private final ProviderProfileService profileService;
    private final ProviderServiceApprovalService approvalService;

    @Transactional
    public ServiceOptionResponse create(Long serviceTypeId, CreateServiceOptionRequest request) {
        requireType(serviceTypeId);
        String code = normalizeCode(request.code());
        if (optionRepository.existsByServiceProviderTypeIdAndCodeIgnoreCase(serviceTypeId, code)) {
            throw new ConflictException("Service option code already exists");
        }
        ServiceOption option = new ServiceOption();
        option.setServiceProviderTypeId(serviceTypeId);
        apply(option, request);
        option.setStatus("ACTIVE");
        return response(optionRepository.save(option));
    }

    @Transactional
    public ServiceOptionResponse update(
            Long serviceTypeId, Long optionId, CreateServiceOptionRequest request) {
        ServiceOption option = requireOption(serviceTypeId, optionId);
        String code = normalizeCode(request.code());
        if (!option.getCode().equalsIgnoreCase(code)
                && optionRepository.existsByServiceProviderTypeIdAndCodeIgnoreCase(
                serviceTypeId, code)) {
            throw new ConflictException("Service option code already exists");
        }
        apply(option, request);
        return response(optionRepository.save(option));
    }

    @Transactional
    public ServiceOptionResponse updateStatus(
            Long serviceTypeId, Long optionId, String requestedStatus) {
        String status = requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }
        ServiceOption option = requireOption(serviceTypeId, optionId);
        option.setStatus(status);
        return response(optionRepository.save(option));
    }

    @Transactional(readOnly = true)
    public List<ServiceOptionResponse> list(Long serviceTypeId, boolean includeInactive) {
        requireType(serviceTypeId);
        List<ServiceOption> options = includeInactive
                ? optionRepository.findAllByServiceProviderTypeIdOrderByDisplayOrderAscNameAsc(serviceTypeId)
                : optionRepository.findAllByServiceProviderTypeIdAndStatusOrderByDisplayOrderAscNameAsc(
                        serviceTypeId, "ACTIVE");
        return options.stream().map(this::response).toList();
    }

    @Transactional
    public List<ProviderServiceOptionResponse> replaceProviderOptions(
            Long userId, Long assignmentId, UpdateProviderOptionsRequest request) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        ServiceProviderType type = requireType(assignment.getServiceProviderTypeId());
        Set<Long> ids = new LinkedHashSet<>(request.optionIds());
        if (ids.size() != request.optionIds().size()) {
            throw new IllegalArgumentException("Service options cannot contain duplicates");
        }
        validateSelectionCount(type, ids.size());
        List<ServiceOption> options = optionRepository
                .findAllByOptionIdInAndServiceProviderTypeIdAndStatus(
                        ids, type.getServiceProviderTypeId(), "ACTIVE");
        if (options.size() != ids.size()) {
            throw new IllegalArgumentException("One or more service options are invalid or inactive");
        }

        Map<Long, ProviderServiceOption> existing = new HashMap<>();
        providerOptionRepository.findAllByAssignmentId(assignmentId)
                .forEach(item -> existing.put(item.getOptionId(), item));
        existing.values().stream().filter(item -> !ids.contains(item.getOptionId()))
                .forEach(providerOptionRepository::delete);
        for (Long id : ids) {
            if (!existing.containsKey(id)) {
                ProviderServiceOption item = new ProviderServiceOption();
                item.setAssignmentId(assignmentId);
                item.setOptionId(id);
                item.setProviderStatus("ACTIVE");
                item.setAdminStatus("PENDING");
                item.setVerificationStatus("PENDING");
                providerOptionRepository.save(item);
            }
        }
        approvalService.refreshProviderApprovals(assignment.getServiceProviderId());
        return providerOptions(assignmentId);
    }

    @Transactional(readOnly = true)
    public List<ProviderServiceOptionResponse> getProviderOptions(Long userId, Long assignmentId) {
        ownedAssignment(userId, assignmentId);
        return providerOptions(assignmentId);
    }

    @Transactional
    public ProviderServiceOptionResponse review(
            Long reviewerId, Long providerOptionId, DocumentReviewRequest request) {
        ProviderServiceOption item = providerOptionRepository.findById(providerOptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider service option not found"));
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        item.setAdminStatus(status);
        item.setVerificationStatus(status);
        item.setReviewedBy(reviewerId);
        item.setReviewedAt(LocalDateTime.now());
        item.setReviewNote(trim(request.rejectionReason()));
        item = providerOptionRepository.save(item);
        ServiceProviderTypeAssignment assignment = assignmentRepository
                .findById(item.getAssignmentId()).orElseThrow();
        approvalService.refreshProviderApprovals(assignment.getServiceProviderId());
        return providerOptionResponse(item, optionRepository.findById(item.getOptionId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public Page<ProviderServiceOptionResponse> reviewQueue(String status, Pageable pageable) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "APPROVED", "REJECTED").contains(normalized)) {
            throw new IllegalArgumentException("Status must be PENDING, APPROVED or REJECTED");
        }
        return providerOptionRepository.findAllByVerificationStatus(normalized, pageable)
                .map(item -> providerOptionResponse(
                        item, optionRepository.findById(item.getOptionId()).orElse(null)));
    }

    @Transactional
    public List<String> replaceLanguages(
            Long userId, Long assignmentId, UpdateProviderLanguagesRequest request) {
        ownedAssignment(userId, assignmentId);
        Set<String> languages = new TreeSet<>();
        request.languages().forEach(value -> languages.add(value.trim().toUpperCase(Locale.ROOT)));
        if (!LANGUAGES.containsAll(languages)) {
            throw new IllegalArgumentException("Unsupported language");
        }
        languageRepository.deleteAllByAssignmentId(assignmentId);
        languages.forEach(code -> {
            ProviderServiceLanguage item = new ProviderServiceLanguage();
            item.setAssignmentId(assignmentId);
            item.setLanguageCode(code);
            languageRepository.save(item);
        });
        return List.copyOf(languages);
    }

    @Transactional(readOnly = true)
    public List<String> getLanguages(Long userId, Long assignmentId) {
        ownedAssignment(userId, assignmentId);
        return languageRepository.findAllByAssignmentIdOrderByLanguageCodeAsc(assignmentId)
                .stream().map(ProviderServiceLanguage::getLanguageCode).toList();
    }

    private List<ProviderServiceOptionResponse> providerOptions(Long assignmentId) {
        List<ProviderServiceOption> selected = providerOptionRepository.findAllByAssignmentId(assignmentId);
        Map<Long, ServiceOption> options = new HashMap<>();
        optionRepository.findAllById(selected.stream().map(ProviderServiceOption::getOptionId).toList())
                .forEach(option -> options.put(option.getOptionId(), option));
        return selected.stream().map(item -> providerOptionResponse(item, options.get(item.getOptionId())))
                .toList();
    }

    private void validateSelectionCount(ServiceProviderType type, int count) {
        int minimum = type.getMinimumOptionSelections() == null
                ? (type.isOptionsRequired() ? 1 : 0) : type.getMinimumOptionSelections();
        if (count < minimum) {
            throw new IllegalArgumentException("Select at least " + minimum + " service option(s)");
        }
        if (type.getMaximumOptionSelections() != null
                && count > type.getMaximumOptionSelections()) {
            throw new IllegalArgumentException(
                    "Select no more than " + type.getMaximumOptionSelections() + " service option(s)");
        }
    }

    private ServiceProviderTypeAssignment ownedAssignment(Long userId, Long assignmentId) {
        ServiceProvider provider = profileService.findByUserId(userId);
        return assignmentRepository.findByAssignmentIdAndServiceProviderId(
                        assignmentId, provider.getServiceProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider service not found"));
    }

    private ServiceProviderType requireType(Long id) {
        return typeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service type not found"));
    }

    private ServiceOption requireOption(Long typeId, Long optionId) {
        return optionRepository.findByOptionIdAndServiceProviderTypeId(optionId, typeId)
                .orElseThrow(() -> new ResourceNotFoundException("Service option not found"));
    }

    private void apply(ServiceOption option, CreateServiceOptionRequest request) {
        option.setCode(normalizeCode(request.code()));
        option.setName(request.name().trim());
        option.setDescription(trim(request.description()));
        option.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        String schedulingModel = request.schedulingModel() == null
                || request.schedulingModel().isBlank()
                ? "TIME_BASED" : normalizeCode(request.schedulingModel());
        if (!SCHEDULING_MODELS.contains(schedulingModel)) {
            throw new IllegalArgumentException(
                    "Scheduling model must be TIME_BASED or ROUTE_BASED");
        }
        option.setSchedulingModel(schedulingModel);
        option.setRouteAverageSpeedKmh("ROUTE_BASED".equals(schedulingModel)
                ? (request.routeAverageSpeedKmh() == null ? 40 : request.routeAverageSpeedKmh())
                : null);
        Set<String> modes = request.deliveryModes() == null || request.deliveryModes().isEmpty()
                ? Set.of("PROVIDER_TO_CUSTOMER") : request.deliveryModes()
                .stream().map(this::normalizeCode).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (modes.isEmpty()) {
            throw new IllegalArgumentException("Select at least one delivery mode");
        }
        if (!DELIVERY_MODES.containsAll(modes)) {
            throw new IllegalArgumentException(
                    "Delivery mode must be PROVIDER_TO_CUSTOMER, CUSTOMER_TO_PROVIDER, or ONLINE");
        }
        if (request.locationEnabled() && modes.equals(Set.of("ONLINE"))) {
            throw new IllegalArgumentException("Online-only options cannot enable physical locations");
        }
        if ("ROUTE_BASED".equals(schedulingModel)
                && (!request.locationEnabled() || !modes.equals(Set.of("PROVIDER_TO_CUSTOMER")))) {
            throw new IllegalArgumentException(
                    "ROUTE_BASED options require location and PROVIDER_TO_CUSTOMER delivery only");
        }
        option.setLocationEnabled(request.locationEnabled());
        option.setDeliveryModes(modes);
        String fulfillmentModel = request.fulfillmentModel() == null
                || request.fulfillmentModel().isBlank()
                ? "ONE_TO_ONE" : normalizeCode(request.fulfillmentModel());
        if (!FULFILLMENT_MODELS.contains(fulfillmentModel)) {
            throw new IllegalArgumentException("Fulfillment model must be ONE_TO_ONE, "
                    + "MANY_CUSTOMERS_ONE_PROVIDER or ONE_CUSTOMER_MANY_PROVIDERS");
        }
        if ("ROUTE_BASED".equals(schedulingModel)
                && !"ONE_TO_ONE".equals(fulfillmentModel)) {
            throw new IllegalArgumentException("ROUTE_BASED options currently support ONE_TO_ONE only");
        }
        option.setFulfillmentModel(fulfillmentModel);
        option.setDefaultCapacity("MANY_CUSTOMERS_ONE_PROVIDER".equals(fulfillmentModel)
                ? (request.defaultCapacity() == null ? 1 : request.defaultCapacity()) : 1);
        option.setRequiredProviderCount("ONE_CUSTOMER_MANY_PROVIDERS".equals(fulfillmentModel)
                ? (request.requiredProviderCount() == null ? 2 : request.requiredProviderCount()) : 1);
        String bookingMode = request.bookingMode() == null || request.bookingMode().isBlank()
                ? "MANY_AT_A_TIME" : normalizeCode(request.bookingMode());
        if (!BOOKING_MODES.contains(bookingMode)) {
            throw new IllegalArgumentException(
                    "Booking mode must be ONE_AT_A_TIME or MANY_AT_A_TIME");
        }
        option.setBookingMode(bookingMode);
    }

    private String normalizeCode(String value) {
        String code = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        if (code.isBlank()) throw new IllegalArgumentException("Service option code is invalid");
        return code;
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private ServiceOptionResponse response(ServiceOption item) {
        return new ServiceOptionResponse(item.getOptionId(), item.getServiceProviderTypeId(),
                item.getCode(), item.getName(), item.getDescription(), item.getDisplayOrder(),
                item.getStatus(), item.getSchedulingModel() == null
                        ? "TIME_BASED" : item.getSchedulingModel(), item.getRouteAverageSpeedKmh(),
                item.isLocationEnabled(), Set.copyOf(item.getDeliveryModes()),
                item.getFulfillmentModel() == null ? "ONE_TO_ONE" : item.getFulfillmentModel(),
                item.getDefaultCapacity() == null ? 1 : item.getDefaultCapacity(),
                item.getRequiredProviderCount() == null ? 1 : item.getRequiredProviderCount(),
                item.getBookingMode() == null ? "MANY_AT_A_TIME" : item.getBookingMode(),
                item.getDeliveryModes().stream().sorted().map(mode -> deliveryConfiguration(
                        mode, item.isLocationEnabled())).toList());
    }

    private ServiceOptionResponse.DeliveryModeConfiguration deliveryConfiguration(
            String mode, boolean locationEnabled) {
        return switch (mode) {
            case "PROVIDER_TO_CUSTOMER" -> new ServiceOptionResponse.DeliveryModeConfiguration(
                    mode, "COVERAGE_AREA", locationEnabled, locationEnabled, "MEETING_POINT");
            case "CUSTOMER_TO_PROVIDER" -> new ServiceOptionResponse.DeliveryModeConfiguration(
                    mode, "FIXED_POINT", locationEnabled, false,
                    "PROVIDER_LOCATION_SELECTION");
            case "ONLINE" -> new ServiceOptionResponse.DeliveryModeConfiguration(
                    mode, "NONE", false, false, "NONE");
            default -> throw new IllegalStateException("Unsupported delivery mode: " + mode);
        };
    }


    private ProviderServiceOptionResponse providerOptionResponse(
            ProviderServiceOption item, ServiceOption option) {
        return new ProviderServiceOptionResponse(item.getProviderOptionId(), item.getOptionId(),
                option == null ? null : option.getCode(), option == null ? null : option.getName(),
                item.getProviderStatus(), item.getAdminStatus(), item.getVerificationStatus(),
                item.getReviewedBy(), item.getReviewedAt(), item.getReviewNote());
    }
}
