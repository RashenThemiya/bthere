package com.jobhub.service.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.job.BookingResponse;
import com.jobhub.dto.job.CreateBookingRequest;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.job.JobCustomFieldAnswer;
import com.jobhub.entity.job.JobProviderAssignment;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.job.JobCustomFieldAnswerRepository;
import com.jobhub.repository.job.JobProviderAssignmentRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.provider.*;
import com.jobhub.service.market.ServicePricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Locale;
import java.util.LinkedHashSet;

@Service
@RequiredArgsConstructor
public class CustomerBookingService {

    private final CustomerRepository customerRepository;
    private final JobRepository jobRepository;
    private final JobCustomFieldAnswerRepository answerRepository;
    private final JobProviderAssignmentRepository jobProviderRepository;
    private final ServiceProviderTypeAssignmentRepository assignmentRepository;
    private final ProviderServiceOptionRepository providerOptionRepository;
    private final ServiceOptionRepository optionRepository;
    private final ServiceProviderServiceAreaRepository areaRepository;
    private final ServiceProviderMarketRepository providerMarketRepository;
    private final MarketRepository marketRepository;
    private final ServiceCustomFieldRepository customFieldRepository;
    private final ServicePricingService pricingService;
    private final ObjectMapper objectMapper;

    @Transactional
    public BookingResponse create(Long userId, CreateBookingRequest request) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Complete the customer profile first"));
        ServiceOption option = optionRepository.findByOptionIdAndServiceProviderTypeId(
                        request.optionId(), request.serviceTypeId())
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Active service option not found"));
        List<Long> providerIds = resolveProviderIds(option, request);
        List<ServiceProviderTypeAssignment> assignments = providerIds.stream()
                .map(providerId -> requireApprovedProvider(providerId,
                        request.serviceTypeId(), request.optionId()))
                .toList();
        ServiceProviderTypeAssignment assignment = assignments.get(0);
        String mode = request.deliveryMode().trim().toUpperCase(Locale.ROOT);
        if (!option.getDeliveryModes().contains(mode)) {
            throw new IllegalArgumentException("Delivery mode is not enabled for this option");
        }

        BookingTime time = resolveTime(option, request);
        Location location = resolveLocation(assignment, option, mode, request);
        Route route = resolveRoute(option, request, location);
        enforceCapacity(option, providerIds, time, location);
        Map<ServiceCustomField, JsonNode> validatedAnswers = validateAnswers(
                userId, request.serviceTypeId(), request.optionId(),
                request.answers() == null ? List.of() : request.answers());
        ServiceProviderMarket providerMarket = providerMarketRepository
                .findByServiceProviderIdAndStatus(providerIds.get(0), "ACTIVE")
                .orElseThrow(() -> new IllegalArgumentException("Provider has no active market"));
        Market market = marketRepository.findById(providerMarket.getMarketId())
                .orElseThrow(() -> new ResourceNotFoundException("Market not found"));
        BigDecimal pricingLatitude = "ROUTE_BASED".equals(schedulingModel(option))
                ? route.destinationLatitude() : location.latitude();
        BigDecimal pricingLongitude = "ROUTE_BASED".equals(schedulingModel(option))
                ? route.destinationLongitude() : location.longitude();
        ServicePricingService.Price price = pricingService.calculate(
                market.getMarketId(), request.serviceTypeId(), request.optionId(),
                schedulingModel(option), time.start(), time.end(), route.distanceKm(),
                pricingLatitude, pricingLongitude);

        Job job = new Job();
        job.setMarketId(market.getMarketId());
        job.setCurrencyCode(market.getDefaultCurrency());
        job.setCustomerId(customer.getCustomerId());
        job.setServiceProviderTypeId(request.serviceTypeId());
        job.setServiceOptionId(request.optionId());
        job.setAssignedServiceProviderId(providerIds.get(0));
        job.setFulfillmentModel(fulfillmentModel(option));
        job.setCapacityUsed(1);
        job.setDeliveryMode(mode);
        job.setSchedulingModel(schedulingModel(option));
        job.setProviderLocationId(location.providerLocationId());
        job.setMeetingPointName(location.name());
        job.setLatitude(location.latitude());
        job.setLongitude(location.longitude());
        job.setAddressLine1(location.addressLine1());
        job.setAddressLine2(location.addressLine2());
        job.setCity(location.city());
        job.setDistrict(location.district());
        job.setProvince(location.province());
        job.setPostalCode(location.postalCode());
        job.setCountry(location.country());
        job.setLocationInstructions(location.instructions());
        job.setStartDatetime(time.start());
        job.setExpectedEndDatetime(time.end());
        job.setDestinationLatitude(route.destinationLatitude());
        job.setDestinationLongitude(route.destinationLongitude());
        job.setDestinationAddress(route.destinationAddress());
        job.setEstimatedDistanceKm(route.distanceKm());
        job.setEstimatedDurationMinutes(route.durationMinutes());
        job.setRateId(price.rateId());
        job.setExpectedAmount(price.amount());
        job.setTotalAmount(price.amount());
        job.setCustomerNote(trim(request.customerNote()));
        job.setJobStatus("PENDING");
        job.setPaymentStatus("PENDING");
        job = jobRepository.save(job);
        saveProviderAssignments(job.getJobId(), providerIds);
        saveAnswers(job.getJobId(), validatedAnswers);
        return response(job);
    }

    private BookingTime resolveTime(ServiceOption option, CreateBookingRequest request) {
        if ("TIME_BASED".equals(schedulingModel(option))) {
            if (request.startDatetime() == null || request.expectedEndDatetime() == null
                    || !request.startDatetime().isBefore(request.expectedEndDatetime())) {
                throw new IllegalArgumentException(
                        "TIME_BASED booking requires startDatetime before expectedEndDatetime");
            }
            if (request.requestedPickupTime() != null
                    || request.pickup() != null || request.destination() != null) {
                throw new IllegalArgumentException(
                        "TIME_BASED booking cannot contain route fields");
            }
            return new BookingTime(request.startDatetime(), request.expectedEndDatetime());
        }
        if ("ROUTE_BASED".equals(schedulingModel(option))) {
            if (request.requestedPickupTime() == null
                    || request.pickup() == null || request.destination() == null) {
                throw new IllegalArgumentException(
                        "ROUTE_BASED booking requires requestedPickupTime, pickup and destination");
            }
            if (request.startDatetime() != null || request.expectedEndDatetime() != null
                    || request.meetingPoint() != null || request.providerLocationId() != null) {
                throw new IllegalArgumentException(
                        "ROUTE_BASED booking cannot contain time-based location fields");
            }
            double distance = distanceKm(request.pickup().latitude(), request.pickup().longitude(),
                    request.destination().latitude(), request.destination().longitude());
            int speed = option.getRouteAverageSpeedKmh() == null
                    ? 40 : option.getRouteAverageSpeedKmh();
            int minutes = Math.max(1, (int) Math.ceil(distance / speed * 60));
            return new BookingTime(request.requestedPickupTime(),
                    request.requestedPickupTime().plusMinutes(minutes));
        }
        throw new IllegalArgumentException("Service option scheduling model is invalid");
    }

    private Route resolveRoute(
            ServiceOption option, CreateBookingRequest request, Location pickupLocation) {
        if (!"ROUTE_BASED".equals(schedulingModel(option))) return Route.empty();
        double distance = distanceKm(request.pickup().latitude(), request.pickup().longitude(),
                request.destination().latitude(), request.destination().longitude());
        int speed = option.getRouteAverageSpeedKmh() == null ? 40 : option.getRouteAverageSpeedKmh();
        int duration = Math.max(1, (int) Math.ceil(distance / speed * 60));
        return new Route(request.destination().latitude(), request.destination().longitude(),
                request.destination().address().trim(),
                BigDecimal.valueOf(distance).setScale(2, java.math.RoundingMode.HALF_UP), duration);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> list(Long userId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Complete the customer profile first"));
        return jobRepository.findAllByCustomerIdOrderByCreatedAtDesc(customer.getCustomerId())
                .stream().map(this::response).toList();
    }

    private Map<ServiceCustomField, JsonNode> validateAnswers(
            Long userId, Long serviceTypeId, Long optionId,
            List<CreateBookingRequest.Answer> submitted) {
        List<ServiceCustomField> applicable = customFieldRepository
                .findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(
                        serviceTypeId, "BOOKING_FIELD", "ACTIVE").stream()
                .filter(field -> field.getOptionId() == null || optionId.equals(field.getOptionId()))
                .toList();
        Map<Long, ServiceCustomField> byId = new HashMap<>();
        Map<String, ServiceCustomField> byCode = new HashMap<>();
        applicable.forEach(field -> {
            byId.put(field.getFieldId(), field);
            byCode.put(field.getCode(), field);
        });
        Map<ServiceCustomField, JsonNode> answers = new LinkedHashMap<>();
        Set<Long> duplicates = new HashSet<>();
        for (CreateBookingRequest.Answer answer : submitted) {
            if (!duplicates.add(answer.fieldId())) {
                throw new IllegalArgumentException("Booking answers cannot contain duplicate fields");
            }
            ServiceCustomField field = byId.get(answer.fieldId());
            if (field == null) {
                throw new IllegalArgumentException("Booking field is not valid for the selected option");
            }
            validateAnswerValue(userId, field, answer.value());
            answers.put(field, answer.value());
        }
        for (ServiceCustomField field : applicable) {
            if (field.isRequired() && conditionApplies(field, byCode, answers)
                    && !answers.containsKey(field)) {
                throw new IllegalArgumentException(field.getLabel() + " is required");
            }
        }
        return answers;
    }

    private void validateAnswerValue(Long userId, ServiceCustomField field, JsonNode value) {
        if (value == null || value.isNull()
                || value.isTextual() && value.asText().isBlank()) {
            throw new IllegalArgumentException(field.getLabel() + " cannot be empty");
        }
        switch (field.getFieldType()) {
            case "NUMBER" -> require(value.isNumber(), field);
            case "BOOLEAN" -> require(value.isBoolean(), field);
            case "MULTI_SELECT" -> require(value.isArray(), field);
            case "IMAGE_UPLOAD", "DOCUMENT_UPLOAD", "FILE_UPLOAD" -> {
                JsonNode files = value.isArray() ? value : value.get("files");
                require(files != null && files.isArray(), field);
                int minimum = field.getMinimumFiles() == null ? (field.isRequired() ? 1 : 0)
                        : field.getMinimumFiles();
                if (files.size() < minimum
                        || field.getMaximumFiles() != null && files.size() > field.getMaximumFiles()) {
                    throw new IllegalArgumentException(field.getLabel() + " has an invalid file count");
                }
                files.forEach(file -> {
                    JsonNode key = file.isTextual() ? file : file.get("key");
                    if (key == null || !key.isTextual()
                            || !key.asText().startsWith("providers/" + userId + "/")) {
                        throw new IllegalArgumentException("Uploaded file does not belong to this customer");
                    }
                });
            }
            default -> require(value.isTextual(), field);
        }
        if (Set.of("SELECT", "MULTI_SELECT").contains(field.getFieldType())) {
            validateAllowedOptions(field, value);
        }
    }

    private void validateAllowedOptions(ServiceCustomField field, JsonNode value) {
        JsonNode configured = read(field.getOptionsJson());
        Set<String> allowed = new HashSet<>();
        if (configured != null) configured.forEach(item -> allowed.add(
                item.isTextual() ? item.asText() : item.path("value").asText()));
        if (field.getFieldType().equals("SELECT") && !allowed.contains(value.asText())) {
            throw new IllegalArgumentException("Invalid option for " + field.getLabel());
        }
        if (field.getFieldType().equals("MULTI_SELECT")) {
            value.forEach(item -> {
                if (!item.isTextual() || !allowed.contains(item.asText())) {
                    throw new IllegalArgumentException("Invalid option for " + field.getLabel());
                }
            });
        }
    }

    private boolean conditionApplies(ServiceCustomField field, Map<String, ServiceCustomField> byCode,
                                     Map<ServiceCustomField, JsonNode> answers) {
        JsonNode condition = read(field.getConditionJson());
        if (condition == null) return true;
        ServiceCustomField parent = byCode.get(condition.path("fieldCode").asText());
        JsonNode actual = parent == null ? null : answers.get(parent);
        if (actual == null) return false;
        JsonNode expected = condition.get("value");
        return "NOT_EQUALS".equals(condition.path("operator").asText("EQUALS"))
                ? !actual.equals(expected) : actual.equals(expected);
    }

    private void saveAnswers(Long jobId, Map<ServiceCustomField, JsonNode> answers) {
        answers.forEach((field, value) -> {
            JobCustomFieldAnswer answer = new JobCustomFieldAnswer();
            answer.setJobId(jobId);
            answer.setFieldId(field.getFieldId());
            answer.setFieldCode(field.getCode());
            answer.setFieldLabel(field.getLabel());
            answer.setFieldVersion(field.getVersion());
            answer.setValueJson(write(value));
            answerRepository.save(answer);
        });
    }

    private Location resolveLocation(ServiceProviderTypeAssignment assignment, ServiceOption option,
                                     String mode, CreateBookingRequest request) {
        if ("ONLINE".equals(mode)) {
            if (request.meetingPoint() != null || request.providerLocationId() != null) {
                throw new IllegalArgumentException("Online bookings cannot contain a physical location");
            }
            return Location.empty();
        }
        if (!option.isLocationEnabled()) {
            throw new IllegalArgumentException("Physical locations are disabled for this option");
        }
        if ("CUSTOMER_TO_PROVIDER".equals(mode)) {
            if (request.providerLocationId() == null || request.meetingPoint() != null) {
                throw new IllegalArgumentException("Select one provider location");
            }
            ServiceProviderServiceArea area = areaRepository.findById(request.providerLocationId())
                    .filter(item -> assignment.getAssignmentId().equals(item.getAssignmentId())
                            && option.getOptionId().equals(item.getOptionId())
                            && mode.equals(item.getDeliveryMode()) && "ACTIVE".equals(item.getStatus()))
                    .orElseThrow(() -> new IllegalArgumentException("Provider location is invalid"));
            return Location.fromArea(area);
        }
        if ("PROVIDER_TO_CUSTOMER".equals(mode)) {
            BigDecimal customerLatitude;
            BigDecimal customerLongitude;
            if ("ROUTE_BASED".equals(schedulingModel(option))) {
                customerLatitude = request.pickup().latitude();
                customerLongitude = request.pickup().longitude();
            } else if (request.meetingPoint() != null && request.providerLocationId() == null) {
                customerLatitude = request.meetingPoint().latitude();
                customerLongitude = request.meetingPoint().longitude();
            } else {
                throw new IllegalArgumentException("Customer meeting point is required");
            }
            List<ServiceProviderServiceArea> areas = areaRepository
                    .findAllByAssignmentIdAndOptionIdAndDeliveryModeOrderByLocationNameAsc(
                            assignment.getAssignmentId(), option.getOptionId(), mode);
            boolean covered = areas.stream().anyMatch(area -> area.getRadiusKm() != null
                    && distanceKm(area.getLatitude(), area.getLongitude(),
                    customerLatitude, customerLongitude)
                    <= area.getRadiusKm().doubleValue());
            if (!covered) {
                throw new IllegalArgumentException("Meeting point is outside the provider service area");
            }
            return "ROUTE_BASED".equals(schedulingModel(option))
                    ? Location.fromRoutePoint(request.pickup())
                    : Location.fromMeetingPoint(request.meetingPoint());
        }
        throw new IllegalArgumentException("Unsupported delivery mode");
    }

    private List<Long> resolveProviderIds(ServiceOption option, CreateBookingRequest request) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (request.providerId() != null) ids.add(request.providerId());
        if (request.providerIds() != null) ids.addAll(request.providerIds());
        if (ids.contains(null)) throw new IllegalArgumentException("Provider IDs cannot contain null");
        String model = fulfillmentModel(option);
        int required = "ONE_CUSTOMER_MANY_PROVIDERS".equals(model)
                ? (option.getRequiredProviderCount() == null ? 2 : option.getRequiredProviderCount()) : 1;
        if (ids.size() != required) {
            throw new IllegalArgumentException("This option requires exactly " + required + " provider(s)");
        }
        return List.copyOf(ids);
    }

    private ServiceProviderTypeAssignment requireApprovedProvider(
            Long providerId, Long serviceTypeId, Long optionId) {
        ServiceProviderTypeAssignment assignment = assignmentRepository
                .findByServiceProviderIdAndServiceProviderTypeId(providerId, serviceTypeId)
                .filter(item -> "ACTIVE".equals(item.getStatus())
                        && "APPROVED".equals(item.getVerificationStatus()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider " + providerId + " is not approved for this service"));
        ProviderServiceOption selected = providerOptionRepository
                .findByAssignmentIdAndOptionId(assignment.getAssignmentId(), optionId)
                .filter(item -> "APPROVED".equals(item.getVerificationStatus())
                        && "ACTIVE".equals(item.getProviderStatus())
                        && "ACTIVE".equals(item.getAdminStatus()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Provider " + providerId + " is not active for this service option"));
        return assignment;
    }

    private void enforceCapacity(ServiceOption option, List<Long> providerIds,
                                 BookingTime time, Location location) {
        Set<String> excluded = Set.of("CANCELLED", "COMPLETED", "REJECTED");
        String model = fulfillmentModel(option);
        String bookingMode = option.getBookingMode() == null
                ? "MANY_AT_A_TIME" : option.getBookingMode();
        if ("ONE_AT_A_TIME".equals(bookingMode)) {
            for (Long providerId : providerIds) {
                long primaryUsed = jobRepository.countProviderServiceOverlapping(
                        providerId, option.getServiceProviderTypeId(),
                        time.start(), time.end(), excluded);
                long assignmentUsed = jobProviderRepository.countProviderServiceOverlapping(
                        providerId, option.getServiceProviderTypeId(),
                        time.start(), time.end(), excluded);
                if (Math.max(primaryUsed, assignmentUsed) > 0) {
                    throw new IllegalArgumentException(
                            "Provider " + providerId + " already has a booking for this service");
                }
            }
            return;
        }
        if ("MANY_CUSTOMERS_ONE_PROVIDER".equals(model)) {
            int capacity = option.getDefaultCapacity() == null ? 1 : option.getDefaultCapacity();
            if (location.providerLocationId() != null) {
                Integer locationCapacity = areaRepository.findById(location.providerLocationId())
                        .map(ServiceProviderServiceArea::getCapacity).orElse(null);
                if (locationCapacity != null) capacity = locationCapacity;
            }
            long used = location.providerLocationId() == null
                    ? jobRepository.countPrimaryOverlapping(providerIds.get(0), option.getOptionId(),
                    time.start(), time.end(), excluded)
                    : jobRepository.countLocationOverlapping(providerIds.get(0), option.getOptionId(),
                    location.providerLocationId(), time.start(), time.end(), excluded);
            if (used >= capacity) {
                throw new IllegalArgumentException("No capacity is available for the selected time");
            }
            return;
        }
        for (Long providerId : providerIds) {
            long primaryUsed = jobRepository.countPrimaryOverlapping(providerId, option.getOptionId(),
                    time.start(), time.end(), excluded);
            long assignmentUsed = jobProviderRepository.countOverlapping(
                    providerId, option.getOptionId(), time.start(), time.end(), excluded);
            long used = Math.max(primaryUsed, assignmentUsed);
            if (used > 0) {
                throw new IllegalArgumentException(
                        "Provider " + providerId + " is unavailable for the selected time");
            }
        }
    }

    private void saveProviderAssignments(Long jobId, List<Long> providerIds) {
        providerIds.forEach(providerId -> {
            JobProviderAssignment item = new JobProviderAssignment();
            item.setJobId(jobId);
            item.setServiceProviderId(providerId);
            item.setStatus("ASSIGNED");
            jobProviderRepository.save(item);
        });
    }

    private String fulfillmentModel(ServiceOption option) {
        return option.getFulfillmentModel() == null ? "ONE_TO_ONE" : option.getFulfillmentModel();
    }

    private double distanceKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double latitudeDistance = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double longitudeDistance = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue()))
                * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private BookingResponse response(Job job) {
        Map<String, JsonNode> answers = new LinkedHashMap<>();
        answerRepository.findAllByJobIdOrderByAnswerIdAsc(job.getJobId())
                .forEach(answer -> answers.put(answer.getFieldCode(), read(answer.getValueJson())));
        List<Long> providerIds = jobProviderRepository
                .findAllByJobIdOrderByJobProviderAssignmentIdAsc(job.getJobId())
                .stream().map(JobProviderAssignment::getServiceProviderId).toList();
        if (providerIds.isEmpty() && job.getAssignedServiceProviderId() != null) {
            providerIds = List.of(job.getAssignedServiceProviderId());
        }
        return new BookingResponse(job.getJobId(), job.getServiceProviderTypeId(),
                job.getServiceOptionId(), job.getAssignedServiceProviderId(), providerIds,
                job.getDeliveryMode(), job.getSchedulingModel(),
                job.getFulfillmentModel() == null ? "ONE_TO_ONE" : job.getFulfillmentModel(),
                job.getCapacityUsed() == null ? 1 : job.getCapacityUsed(),
                job.getProviderLocationId(), job.getMeetingPointName(), job.getLatitude(),
                job.getLongitude(), job.getStartDatetime(), job.getExpectedEndDatetime(),
                job.getDestinationLatitude(), job.getDestinationLongitude(),
                job.getDestinationAddress(), job.getEstimatedDistanceKm(),
                job.getEstimatedDurationMinutes(), job.getRateId(), job.getCurrencyCode(),
                job.getExpectedAmount(), job.getJobStatus(), answers);
    }

    private void require(boolean valid, ServiceCustomField field) {
        if (!valid) throw new IllegalArgumentException("Invalid value for " + field.getLabel());
    }

    private String schedulingModel(ServiceOption option) {
        return option.getSchedulingModel() == null ? "TIME_BASED" : option.getSchedulingModel();
    }

    private String write(JsonNode value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid booking answer");
        }
    }

    private JsonNode read(String value) {
        if (value == null || value.isBlank()) return null;
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored booking answer is invalid");
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Location(Long providerLocationId, String name, BigDecimal latitude,
                            BigDecimal longitude, String addressLine1, String addressLine2,
                            String city, String district, String province, String postalCode,
                            String country, String instructions) {
        static Location empty() {
            return new Location(null, null, null, null, null, null, null, null, null, null, null, null);
        }
        static Location fromArea(ServiceProviderServiceArea area) {
            return new Location(area.getServiceAreaId(), area.getLocationName(), area.getLatitude(),
                    area.getLongitude(), null, null, area.getCity(), area.getDistrict(),
                    area.getProvince(), null, area.getCountry(), null);
        }
        static Location fromMeetingPoint(CreateBookingRequest.MeetingPoint point) {
            return new Location(null, point.name().trim(), point.latitude(), point.longitude(),
                    point.addressLine1().trim(), point.addressLine2(), point.city(), point.district(),
                    point.province(), point.postalCode(), point.country(), point.instructions());
        }
        static Location fromRoutePoint(CreateBookingRequest.RoutePoint point) {
            return new Location(null, "Pickup", point.latitude(), point.longitude(),
                    point.address().trim(), null, null, null, null, null, null, null);
        }
    }

    private record BookingTime(java.time.LocalDateTime start, java.time.LocalDateTime end) {}

    private record Route(BigDecimal destinationLatitude, BigDecimal destinationLongitude,
                         String destinationAddress, BigDecimal distanceKm, Integer durationMinutes) {
        static Route empty() { return new Route(null, null, null, null, null); }
    }
}
