package com.jobhub.service.customer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jobhub.dto.customer.CustomerDiscoveryApi.MapMarker;
import com.jobhub.dto.customer.CustomerDiscoveryApi.ProviderFilter;
import com.jobhub.dto.job.LiveTrackingApi.*;
import com.jobhub.dto.market.PricingConditionRequest;
import com.jobhub.entity.job.*;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.exception.TooManyRequestsException;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.job.*;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.repository.provider.ServiceProviderTypeAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Generic live tracking for any service option that sends the provider to the customer.
 * Tracking for a provider on a job opens on confirmation (bookings starting within the option's auto-start
 * window) or when the provider presses Start journey, and closes by job status: EN_ROUTE stops at arrival or
 * service start, FULL_TRIP continues until the provider requests completion.
 */
@Service @RequiredArgsConstructor
public class LiveTrackingService {
    public static final String NONE = "NONE", EN_ROUTE = "EN_ROUTE", FULL_TRIP = "FULL_TRIP";
    static final Set<String> OPEN_JOB_STATUSES = Set.of("CONFIRMED", "IN_PROGRESS");
    static final Set<String> BEFORE_CONFIRMATION = Set.of("PENDING", "AWAITING_PAYMENT");
    static final int UPDATE_INTERVAL_SECONDS = 4, STALE_AFTER_SECONDS = 30, DEFAULT_AUTO_START_MINUTES = 30;
    static final Duration MIN_UPDATE_GAP = Duration.ofSeconds(2), MAX_POINT_AGE = Duration.ofMinutes(2),
            MAX_CLOCK_SKEW = Duration.ofSeconds(30), LOCATION_RETENTION = Duration.ofDays(1);

    private final JobRepository jobs;
    private final JobProviderTrackingRepository trackings;
    private final ProviderLiveLocationRepository locations;
    private final JobProviderAssignmentRepository jobAssignments;
    private final ServiceOptionRepository options;
    private final ServiceProviderRepository providers;
    private final ServiceProviderTypeAssignmentRepository assignments;
    private final CustomerRepository customers;
    private final MarketRepository markets;
    private final BookingRequestRepository requests;
    private final BookingDispatchEventRepository events;
    private final ProviderSearchFields searchFields;
    private final MapMarkerResolver markers;
    private final ObjectMapper mapper;

    // ---------- admin settings ----------

    @Transactional(readOnly = true)
    public Settings settings(Long optionId) {
        return settingsView(option(optionId));
    }

    @Transactional
    public Settings updateSettings(Long optionId, Settings input) {
        var option = options.findLockedById(optionId).orElseThrow(() -> new ResourceNotFoundException("Option not found"));
        if (!NONE.equals(input.mode()) && !option.getDeliveryModes().contains("PROVIDER_TO_CUSTOMER"))
            throw new IllegalArgumentException("Live tracking needs the PROVIDER_TO_CUSTOMER delivery mode on this option");
        option.setLiveTrackingMode(input.mode());
        option.setLiveTrackingAutoStartMinutes(input.autoStartWithinMinutes());
        return settingsView(option);
    }

    private Settings settingsView(ServiceOption option) {
        return new Settings(mode(option), autoStartMinutes(option));
    }

    public static String mode(ServiceOption option) {
        String mode = option == null ? null : option.getLiveTrackingMode();
        return mode == null || mode.isBlank() ? NONE : mode;
    }

    static int autoStartMinutes(ServiceOption option) {
        return option.getLiveTrackingAutoStartMinutes() == null ? DEFAULT_AUTO_START_MINUTES : option.getLiveTrackingAutoStartMinutes();
    }

    /** The option setting applies only when this booking actually sends the provider to the customer. */
    static String mode(Job job, ServiceOption option) {
        return "PROVIDER_TO_CUSTOMER".equals(job.getDeliveryMode()) ? mode(option) : NONE;
    }

    // ---------- lifecycle hook ----------

    /** Called when a booking becomes CONFIRMED. Bookings for "now" start tracking without a button. */
    @Transactional
    public void onConfirmed(Job job) {
        var option = options.findById(job.getServiceOptionId()).orElse(null);
        if (option == null || NONE.equals(mode(job, option)) || job.getStartDatetime() == null) return;
        Instant now = Instant.now();
        Instant start = job.getStartDatetime().atZone(zone(job)).toInstant();
        if (start.isAfter(now.plus(Duration.ofMinutes(autoStartMinutes(option))))) return;
        boolean started = false;
        for (Long providerId : providerIds(job)) {
            if (trackings.findByJobIdAndServiceProviderId(job.getJobId(), providerId).isPresent()) continue;
            trackings.save(newTracking(job.getJobId(), providerId, "AUTO", now));
            started = true;
        }
        if (started) customerEvent(job, "PROVIDER_EN_ROUTE");
    }

    // ---------- provider ----------

    @Transactional(readOnly = true)
    public ProviderState providerState(Long userId, Long jobId) {
        Long providerId = providerId(userId);
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        requireOnJob(job, providerId);
        return providerView(job, providerId);
    }

    @Transactional
    public ProviderState startJourney(Long userId, Long jobId) {
        Long providerId = providerId(userId);
        var job = jobs.lockPaymentJob(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        requireOnJob(job, providerId);
        if (NONE.equals(mode(job, option(job.getServiceOptionId()))))
            throw new ConflictException("Live tracking is not enabled for this booking");
        if (trackings.findByJobIdAndServiceProviderId(jobId, providerId).isPresent()) return providerView(job, providerId);
        if (!"CONFIRMED".equals(job.getJobStatus()))
            throw new ConflictException("Start journey is available only for a confirmed booking that has not started");
        trackings.save(newTracking(jobId, providerId, "MANUAL", Instant.now()));
        customerEvent(job, "PROVIDER_EN_ROUTE");
        return providerView(job, providerId);
    }

    @Transactional
    public ProviderState arrived(Long userId, Long jobId) {
        Long providerId = providerId(userId);
        var job = jobs.lockPaymentJob(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        requireOnJob(job, providerId);
        var tracking = trackings.findByJobIdAndServiceProviderId(jobId, providerId)
                .orElseThrow(() -> new ConflictException("Start the journey before marking arrival"));
        if (tracking.getArrivedAt() != null) return providerView(job, providerId);
        if (!"CONFIRMED".equals(job.getJobStatus()))
            throw new ConflictException("Arrival can be marked only before the booking starts");
        tracking.setArrivedAt(Instant.now());
        customerEvent(job, "PROVIDER_ARRIVED");
        return providerView(job, providerId);
    }

    @Transactional
    public LocationAccepted updateLocation(Long userId, LocationUpdate input) {
        Long providerId = providerId(userId);
        Instant now = Instant.now();
        if (input.recordedAt().isAfter(now.plus(MAX_CLOCK_SKEW)) || input.recordedAt().isBefore(now.minus(MAX_POINT_AGE)))
            throw new IllegalArgumentException("recordedAt must be within the last 2 minutes");
        List<Long> sharedWith = new ArrayList<>();
        for (var tracking : trackings.findOpenForProvider(providerId, OPEN_JOB_STATUSES)) {
            var job = jobs.findById(tracking.getJobId()).orElse(null);
            if (job != null && sharing(tracking, job, mode(job, option(job.getServiceOptionId())))) sharedWith.add(job.getJobId());
        }
        if (sharedWith.isEmpty()) throw new ConflictException("No booking is currently sharing your live location");
        var location = locations.findById(providerId).orElseGet(() -> {
            var created = new ProviderLiveLocation(); created.setServiceProviderId(providerId); return created;
        });
        if (location.getReceivedAt() != null && now.isBefore(location.getReceivedAt().plus(MIN_UPDATE_GAP)))
            throw new TooManyRequestsException("Location updates are limited to one every 2 seconds", 2);
        // An older buffered point never replaces a newer one.
        if (location.getRecordedAt() == null || !input.recordedAt().isBefore(location.getRecordedAt())) {
            location.setLatitude(input.latitude()); location.setLongitude(input.longitude());
            location.setHeadingDegrees(input.headingDegrees()); location.setSpeedKmh(input.speedKmh());
            location.setAccuracyMeters(input.accuracyMeters()); location.setRecordedAt(input.recordedAt());
            location.setReceivedAt(now);
            locations.save(location);
        }
        Collections.sort(sharedWith);
        return new LocationAccepted(now, sharedWith, UPDATE_INTERVAL_SECONDS);
    }

    private ProviderState providerView(Job job, Long providerId) {
        String mode = mode(job, option(job.getServiceOptionId()));
        var tracking = trackings.findByJobIdAndServiceProviderId(job.getJobId(), providerId).orElse(null);
        String status = status(tracking, job, mode);
        boolean confirmed = "CONFIRMED".equals(job.getJobStatus());
        return new ProviderState(job.getJobId(), job.getJobStatus(), mode, status,
                tracking == null ? null : tracking.getStartMode(),
                !NONE.equals(mode) && confirmed && tracking == null,
                confirmed && tracking != null && tracking.getArrivedAt() == null,
                sharing(tracking, job, mode), UPDATE_INTERVAL_SECONDS,
                tracking == null ? null : tracking.getJourneyStartedAt(),
                tracking == null ? null : tracking.getArrivedAt(), target(job));
    }

    // ---------- customer / admin ----------

    @Transactional(readOnly = true)
    public CustomerTracking customerTracking(Long userId, Long jobId) {
        var customer = customers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        var job = jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!customer.getCustomerId().equals(job.getCustomerId())) throw new ResourceNotFoundException("Booking not found");
        return customerView(job);
    }

    @Transactional(readOnly = true)
    public CustomerTracking adminTracking(Long jobId) {
        return customerView(jobs.findById(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found")));
    }

    private CustomerTracking customerView(Job job) {
        var option = option(job.getServiceOptionId());
        String mode = mode(job, option);
        Target target = target(job);
        Instant now = Instant.now();
        var publicFields = searchFields.definitions(job.getServiceProviderTypeId(), job.getServiceOptionId());
        MapMarker marker = markers.resolve(job.getServiceProviderTypeId(), option, publicFields, pricingFilters(job));
        List<TrackedProvider> tracked = new ArrayList<>();
        for (Long providerId : providerIds(job)) {
            var provider = providers.findById(providerId).orElse(null);
            if (provider == null) continue;
            var tracking = trackings.findByJobIdAndServiceProviderId(job.getJobId(), providerId).orElse(null);
            String status = status(tracking, job, mode);
            Location location = null; boolean stale = false; Double distance = null; Integer eta = null;
            if (sharing(tracking, job, mode)) {
                var latest = locations.findById(providerId).orElse(null);
                // Never reveal a position recorded before this job's journey started.
                if (latest != null && !latest.getReceivedAt().isBefore(tracking.getJourneyStartedAt())) {
                    location = new Location(latest.getLatitude(), latest.getLongitude(), latest.getHeadingDegrees(),
                            latest.getSpeedKmh(), latest.getAccuracyMeters(), latest.getRecordedAt(), latest.getReceivedAt());
                    stale = latest.getReceivedAt().isBefore(now.minusSeconds(STALE_AFTER_SECONDS));
                    if (target != null && target.latitude() != null) {
                        double km = distanceKm(latest.getLatitude(), latest.getLongitude(), target.latitude(), target.longitude());
                        distance = Math.round(km * 10.0) / 10.0;
                        int speed = option.getRouteAverageSpeedKmh() == null || option.getRouteAverageSpeedKmh() <= 0
                                ? 30 : option.getRouteAverageSpeedKmh();
                        eta = km < 0.05 ? 0 : (int) Math.ceil(km / speed * 60);
                    }
                }
            }
            Map<Long, com.fasterxml.jackson.databind.JsonNode> attributes = assignments
                    .findByServiceProviderIdAndServiceProviderTypeId(providerId, job.getServiceProviderTypeId())
                    .map(a -> searchFields.attributes(a, publicFields)).orElse(Map.of());
            tracked.add(new TrackedProvider(providerId,
                    (Objects.toString(provider.getFirstName(), "") + " " + Objects.toString(provider.getLastName(), "")).trim(),
                    provider.getProfilePhoto(), provider.getAverageRating(), provider.getTotalRatings(), attributes, marker,
                    status, tracking == null ? null : tracking.getStartMode(),
                    tracking == null ? null : tracking.getJourneyStartedAt(),
                    tracking == null ? null : tracking.getArrivedAt(), location, stale, distance, eta));
        }
        return new CustomerTracking(job.getJobId(), job.getJobStatus(), mode, overallStatus(tracked, job, mode),
                UPDATE_INTERVAL_SECONDS, STALE_AFTER_SECONDS, target, searchFields.displaySchema(publicFields), tracked);
    }

    private static String overallStatus(List<TrackedProvider> tracked, Job job, String mode) {
        for (String status : List.of("IN_TRIP", "EN_ROUTE", "ARRIVED", "NOT_STARTED"))
            if (tracked.stream().anyMatch(p -> status.equals(p.status()))) return status;
        return status(null, job, mode);
    }

    // ---------- rules ----------

    static boolean sharing(JobProviderTracking tracking, Job job, String mode) {
        if (tracking == null || !OPEN_JOB_STATUSES.contains(job.getJobStatus())) return false;
        if (FULL_TRIP.equals(mode)) return true;
        return EN_ROUTE.equals(mode) && "CONFIRMED".equals(job.getJobStatus()) && tracking.getArrivedAt() == null;
    }

    static String status(JobProviderTracking tracking, Job job, String mode) {
        if (NONE.equals(mode)) return "NOT_SUPPORTED";
        String jobStatus = job.getJobStatus();
        if (!OPEN_JOB_STATUSES.contains(jobStatus))
            return jobStatus == null || BEFORE_CONFIRMATION.contains(jobStatus) ? "WAITING_FOR_CONFIRMATION" : "ENDED";
        if ("IN_PROGRESS".equals(jobStatus)) return FULL_TRIP.equals(mode) && tracking != null ? "IN_TRIP" : "ENDED";
        if (tracking == null) return "NOT_STARTED";
        return tracking.getArrivedAt() != null ? "ARRIVED" : "EN_ROUTE";
    }

    /** Pickup/service location until the service starts, then the destination when the booking has one. */
    static Target target(Job job) {
        boolean hasDestination = job.getDestinationLatitude() != null && job.getDestinationLongitude() != null;
        if ("IN_PROGRESS".equals(job.getJobStatus()) && hasDestination)
            return new Target("DESTINATION", job.getDestinationLatitude(), job.getDestinationLongitude(), job.getDestinationAddress());
        if (job.getLatitude() == null || job.getLongitude() == null) return null;
        String address = job.getAddressLine1() == null ? job.getMeetingPointName() : job.getAddressLine1();
        return new Target(hasDestination ? "PICKUP" : "SERVICE_LOCATION", job.getLatitude(), job.getLongitude(), address);
    }

    static double distanceKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double p1 = Math.toRadians(lat1.doubleValue()), p2 = Math.toRadians(lat2.doubleValue());
        double dp = p2 - p1, dl = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ---------- helpers ----------

    /** The assigned provider plus any additional providers on multi-provider jobs. */
    private List<Long> providerIds(Job job) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (job.getAssignedServiceProviderId() != null) ids.add(job.getAssignedServiceProviderId());
        jobAssignments.findAllByJobIdOrderByJobProviderAssignmentIdAsc(job.getJobId()).stream()
                .filter(a -> a.getStatus() == null || !Set.of("CANCELLED", "REMOVED", "DECLINED").contains(a.getStatus()))
                .forEach(a -> ids.add(a.getServiceProviderId()));
        return List.copyOf(ids);
    }

    private void requireOnJob(Job job, Long providerId) {
        if (!providerIds(job).contains(providerId)) throw new ResourceNotFoundException("Booking not found");
    }

    private Long providerId(Long userId) {
        return providers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Provider not found")).getServiceProviderId();
    }

    private ServiceOption option(Long optionId) {
        return options.findById(optionId).orElseThrow(() -> new ResourceNotFoundException("Option not found"));
    }

    private ZoneId zone(Job job) {
        return ZoneId.of(markets.findById(job.getMarketId()).orElseThrow().getTimezone());
    }

    private static JobProviderTracking newTracking(Long jobId, Long providerId, String startMode, Instant at) {
        var tracking = new JobProviderTracking();
        tracking.setJobId(jobId); tracking.setServiceProviderId(providerId);
        tracking.setStartMode(startMode); tracking.setJourneyStartedAt(at);
        return tracking;
    }

    private List<ProviderFilter> pricingFilters(Job job) {
        if (job.getPricingSelectionsJson() == null || job.getPricingSelectionsJson().isBlank()) return List.of();
        try {
            List<PricingConditionRequest> selections = mapper.readValue(job.getPricingSelectionsJson(), new TypeReference<>() {});
            if (selections == null) return List.of();
            return selections.stream().map(s -> new ProviderFilter(s.fieldId(),
                    JsonNodeFactory.instance.arrayNode().add(s.choiceValue()), null, null)).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private void customerEvent(Job job, String type) {
        requests.findByJobId(job.getJobId()).ifPresent(r -> {
            var event = new BookingDispatchEvent();
            event.setUserId(r.getUserId()); event.setRequestId(r.getRequestId()); event.setEventType(type);
            event.setCreatedAt(Instant.now()); event.setNextDeliveryAt(Instant.now());
            events.save(event);
        });
    }

    /** Positions are only needed while a trip is live; drop anything older than a day. */
    @Scheduled(fixedDelayString = "${tracking.location-cleanup-ms:3600000}")
    @Transactional
    public void purgeOldLocations() {
        locations.deleteReceivedBefore(Instant.now().minus(LOCATION_RETENTION));
    }
}
