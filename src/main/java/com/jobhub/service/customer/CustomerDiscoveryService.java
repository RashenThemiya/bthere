package com.jobhub.service.customer;

import com.jobhub.dto.customer.CustomerDiscoveryApi.*;
import com.jobhub.dto.customer.CustomerRequirementsApi.Eligibility;
import com.jobhub.dto.provider.*;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.*;
import com.jobhub.service.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.math.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerDiscoveryService {
    private final CustomerProfileService profiles;
    private final CustomerRequirementsService requirements;
    private final CustomerAvailabilityService availability;
    private final ProviderTypeService types;
    private final ServiceOptionService optionService;
    private final ServiceCustomFieldService fields;
    private final ServiceOptionRepository options;
    private final ServiceProviderTypeAssignmentRepository assignments;
    private final ServiceProviderRepository providers;
    private final ServiceProviderServiceAreaRepository areas;
    private final ProviderServiceLanguageRepository languages;
    private final ProviderServiceSkillRepository skills;
    private final ServiceTypeSkillRepository configuredSkills;
    private final ProviderSearchFields providerSearchFields;
    private final MapMarkerResolver mapMarkerResolver;

    public List<ServiceCard> services(Long userId, Long marketId) {
        profiles.get(userId); availability.market(marketId);
        List<ServiceCard> result = new ArrayList<>();
        for (var type : types.listActiveTypes()) {
            // Catching an exception from a transactional collaborator poisons this transaction.
            if (!availability.hasActiveOffering(marketId, type.id())) continue;
            var access = access(requirements.eligibility(userId, type.id(), null));
            if (access.eligibility().eligible() && optionService.list(type.id(), false).stream()
                    .anyMatch(o -> !requirements.eligibility(userId, type.id(), o.optionId()).eligible())) {
                access = new Access("CHECK_OPTION", "SELECT_OPTION", access.eligibility());
            }
            result.add(new ServiceCard(type, access));
        }
        return result;
    }
    public List<OptionCard> options(Long userId, Long marketId, Long serviceId) {
        profiles.get(userId); availability.requireOffering(marketId, serviceId);
        return optionService.list(serviceId, false).stream().map(o -> new OptionCard(o,
                access(requirements.eligibility(userId, serviceId, o.optionId())))).toList();
    }
    public Context context(Long userId, Long marketId, Long serviceId, Long optionId) {
        var card = options(userId, marketId, serviceId).stream().filter(o -> optionId.equals(o.option().optionId()))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Active service option not found"));
        return new Context(marketId, availability.market(marketId).getTimezone(), card,
                fields.schema(serviceId, "BOOKING_FIELD", optionId), List.of("languages", "requiredSkillIds", "providerFilters"), false,
                providerSearchFields.schema(providerSearchFields.definitions(serviceId, optionId)));
    }
    public Results search(Long userId, Search request) {
        profiles.get(userId);
        availability.requireOffering(request.marketId(), request.serviceTypeId());
        requirements.requireEligible(userId, request.serviceTypeId(), request.optionId());
        var option = options.findByOptionIdAndServiceProviderTypeId(request.optionId(), request.serviceTypeId())
                .filter(o -> o.activeForService(request.serviceTypeId())).orElseThrow(() -> new ResourceNotFoundException("Active option not found"));
        var market = availability.market(request.marketId());
        var publicFields = providerSearchFields.definitions(request.serviceTypeId(), request.optionId());
        providerSearchFields.validate(publicFields, request.providerFilters());
        MapMarker mapMarker = mapMarkerResolver.resolve(request.serviceTypeId(), option, publicFields, request.providerFilters());
        ZoneId zone = ZoneId.of(market.getTimezone());
        String mode = request.deliveryMode().trim().toUpperCase(Locale.ROOT);
        if (!option.getDeliveryModes().contains(mode)) throw new IllegalArgumentException("Delivery mode is not enabled");
        boolean route = "ROUTE_BASED".equals(option.getSchedulingModel());
        OffsetDateTime endAt = request.endAt();
        Point centre = request.location();
        if (route) {
            if (request.pickup() == null || request.destination() == null || endAt != null || centre != null) {
                throw new IllegalArgumentException("Route search requires pickup/destination and no location/endAt");
            }
            centre = request.pickup();
            double distance = CustomerAvailabilityService.distance(centre.latitude(), centre.longitude(),
                    request.destination().latitude(), request.destination().longitude());
            int speed = option.getRouteAverageSpeedKmh() == null ? 40 : option.getRouteAverageSpeedKmh();
            endAt = request.startAt().plusMinutes(Math.max(1, (long) Math.ceil(distance / speed * 60)));
        } else if (request.pickup() != null || request.destination() != null) {
            throw new IllegalArgumentException("Time-based search cannot contain route points");
        }
        if (endAt == null || !request.startAt().isBefore(endAt)
                || request.startAt().toInstant().isBefore(Instant.now())
                || Duration.between(request.startAt(), endAt).compareTo(Duration.ofDays(31)) > 0) {
            throw new IllegalArgumentException("Choose a future interval of at most 31 days");
        }
        if ("ONLINE".equals(mode) && centre != null || !"ONLINE".equals(mode) && centre == null) {
            throw new IllegalArgumentException("Physical searches require location; ONLINE searches do not accept location");
        }
        List<Long> requiredSkills = request.requiredSkillIds() == null ? List.of() : request.requiredSkillIds();
        Set<Long> allowedSkills = new HashSet<>();
        configuredSkills.findAllByServiceProviderTypeIdOrderByNameAsc(request.serviceTypeId()).forEach(s -> allowedSkills.add(s.getSkillId()));
        if (!allowedSkills.containsAll(requiredSkills)) throw new IllegalArgumentException("Skill is not configured for this service");
        LocalDateTime start = request.startAt().atZoneSameInstant(zone).toLocalDateTime();
        LocalDateTime end = endAt.atZoneSameInstant(zone).toLocalDateTime();
        // Existing booking storage uses market-local LocalDateTime. Reject ambiguous DST
        // intervals until that storage contract can represent repeated/nonexistent hours.
        requireUnambiguous(zone, start, end);
        double radius = request.radiusKm() == null ? 50 : request.radiusKm().doubleValue();
        List<Candidate> candidates = new ArrayList<>();
        for (var assignment : assignments.findAllByServiceProviderTypeId(request.serviceTypeId())) {
            if (!availability.qualified(assignment, option, request.marketId()) || !availability.scheduled(assignment, start, end)) continue;
            List<String> spoken = languages.findAllByAssignmentIdOrderByLanguageCodeAsc(assignment.getAssignmentId())
                    .stream().map(ProviderServiceLanguage::getLanguageCode).toList();
            if (request.languages() != null && !spoken.containsAll(request.languages())) continue;
            Set<Long> approvedSkills = new HashSet<>();
            skills.findAllByAssignmentId(assignment.getAssignmentId()).stream().filter(s -> "APPROVED".equals(s.getVerificationStatus()))
                    .forEach(s -> approvedSkills.add(s.getSkillId()));
            if (!approvedSkills.containsAll(requiredSkills)) continue;
            var attributes = providerSearchFields.attributes(assignment, publicFields);
            if (!providerSearchFields.matches(attributes, request.providerFilters())) continue;
            var provider = providers.findById(assignment.getServiceProviderId()).orElseThrow();
            if ("ONLINE".equals(mode)) {
                int remaining = availability.remaining(assignment, option, null, null, start, end, false);
                if (remaining > 0) candidates.add(candidate(provider, assignment, option, null, null, null, remaining, spoken, attributes, mapMarker));
                continue;
            }
            if (!option.isLocationEnabled()) continue;
            List<ServiceProviderServiceArea> matching = new ArrayList<>();
            for (var area : areas.findAllByAssignmentIdAndOptionIdAndDeliveryModeOrderByLocationNameAsc(
                    assignment.getAssignmentId(), option.getOptionId(), mode)) {
                if (!"ACTIVE".equals(area.getStatus()) || !request.marketId().equals(area.getMarketId())
                        || area.getLatitude() == null || area.getLongitude() == null) continue;
                double distance = CustomerAvailabilityService.distance(centre.latitude(), centre.longitude(), area.getLatitude(), area.getLongitude());
                if (distance > radius || "PROVIDER_TO_CUSTOMER".equals(mode)
                        && (area.getRadiusKm() == null || distance > area.getRadiusKm().doubleValue())) continue;
                matching.add(area);
            }
            Point origin = centre;
            matching.sort(Comparator.comparingDouble(a -> CustomerAvailabilityService.distance(origin.latitude(), origin.longitude(), a.getLatitude(), a.getLongitude())));
            for (var area : matching) {
                boolean fixed = "CUSTOMER_TO_PROVIDER".equals(mode);
                int remaining = availability.remaining(assignment, option, fixed ? area.getServiceAreaId() : null,
                        fixed ? area.getCapacity() : null, start, end, false);
                if (remaining <= 0) continue;
                Point pin = new Point(fixed ? area.getLatitude() : area.getLatitude().setScale(2, RoundingMode.HALF_UP),
                        fixed ? area.getLongitude() : area.getLongitude().setScale(2, RoundingMode.HALF_UP));
                double distance = CustomerAvailabilityService.distance(centre.latitude(), centre.longitude(), pin.latitude(), pin.longitude());
                candidates.add(candidate(provider, assignment, option, fixed ? area.getServiceAreaId() : null, pin,
                        Math.round(distance * 10.0)/10.0, remaining, spoken, attributes, mapMarker));
                if (!fixed) break;
            }
        }
        candidates.sort(Comparator.comparing(Candidate::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Candidate::providerId).thenComparing(Candidate::providerLocationId, Comparator.nullsFirst(Comparator.naturalOrder())));
        int page = request.page() == null ? 0 : request.page(), size = request.pageSize() == null ? 20 : request.pageSize();
        int from = (int) Math.min((long) page * size, candidates.size()), until = Math.min(from + size, candidates.size());
        int count = "ONE_CUSTOMER_MANY_PROVIDERS".equals(option.getFulfillmentModel()) ? option.getRequiredProviderCount() : 1;
        long distinct = candidates.stream().map(Candidate::providerId).distinct().count();
        return new Results(Instant.now(), request.marketId(), zone.getId(), request.startAt(), endAt,
                option.getProviderSelectionMode(), count, distinct < count ? "NO_AVAILABLE_PROVIDERS" : "AVAILABLE",
                candidates.size(), page, size, until < candidates.size(),
                distinct < count ? List.of() : candidates.subList(from, until));
    }
    public static void requireUnambiguous(ZoneId zone, LocalDateTime start, LocalDateTime end) {
        var transition = zone.getRules().nextTransition(start.atZone(zone).toInstant().minusNanos(1));
        if (zone.getRules().getValidOffsets(start).size() != 1 || zone.getRules().getValidOffsets(end).size() != 1
                || transition != null && transition.getInstant().isBefore(end.atZone(zone).toInstant())) {
            throw new IllegalArgumentException("Intervals crossing timezone transitions are not supported by current booking storage");
        }
    }
    private Candidate candidate(ServiceProvider p, ServiceProviderTypeAssignment a, ServiceOption o, Long location,
                                Point pin, Double distance, int remaining, List<String> spoken,
                                Map<Long, com.fasterxml.jackson.databind.JsonNode> attributes, MapMarker mapMarker) {
        return new Candidate(p.getServiceProviderId(), a.getAssignmentId(), o.getOptionId(), location,
                p.getFirstName() + " " + p.getLastName(), p.getProfilePhoto(), p.getAverageRating(), p.getTotalRatings(),
                pin == null ? "ONLINE" : location != null ? "PUBLIC_SERVICE_LOCATION" : "APPROXIMATE_SERVICE_AREA",
                pin, distance, remaining, spoken, attributes, mapMarker);
    }
    private Access access(Eligibility result) {
        if (result.eligible()) return new Access("ELIGIBLE", "CONTINUE", result);
        if (result.reasons().stream().anyMatch(r -> "MINIMUM_AGE".equals(r.code()))) return new Access("RESTRICTED", "VIEW_REQUIREMENTS", result);
        if (result.reasons().stream().allMatch(r -> "PENDING".equals(r.code()))) return new Access("PENDING_REVIEW", "WAIT_FOR_REVIEW", result);
        if (result.reasons().stream().anyMatch(r -> "REJECTED".equals(r.code()))) return new Access("REJECTED", "COMPLETE_REQUIREMENTS", result);
        return new Access("NEEDS_DETAILS", "COMPLETE_REQUIREMENTS", result);
    }
}
