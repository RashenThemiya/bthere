package com.jobhub.service.customer;

import com.jobhub.entity.job.Job;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.market.*;
import com.jobhub.repository.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerAvailabilityService {
    private final MarketRepository markets;
    private final MarketServiceOfferingRepository offerings;
    private final ServiceProviderTypeRepository types;
    private final ServiceProviderRepository providers;
    private final ServiceProviderMarketRepository providerMarkets;
    private final ProviderServiceOptionRepository selectedOptions;
    private final ServiceProviderAvailabilityRepository schedules;
    private final ServiceOptionRepository options;
    private final JobRepository jobs;

    public Market market(Long id) {
        return markets.findById(id).filter(m -> "ACTIVE".equals(m.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Active market not found"));
    }
    public void requireOffering(Long marketId, Long serviceId) {
        market(marketId);
        if (!hasActiveOffering(marketId, serviceId)) {
            throw new ResourceNotFoundException("Service is not offered in this market");
        }
    }
    /** The caller validates the market; absent offerings are normal catalog exclusions. */
    public boolean hasActiveOffering(Long marketId, Long serviceId) {
        return types.findById(serviceId).filter(t -> "ACTIVE".equals(t.getStatus())).isPresent()
                && offerings.findByMarketIdAndServiceProviderTypeId(marketId, serviceId)
                .filter(o -> "ACTIVE".equals(o.getStatus())).isPresent();
    }
    public boolean qualified(ServiceProviderTypeAssignment assignment, ServiceOption option, Long marketId) {
        if (!"ACTIVE".equals(assignment.getStatus()) || !"ACTIVE".equals(assignment.getProviderStatus())
                || !"ACTIVE".equals(assignment.getAdminStatus()) || !"APPROVED".equals(assignment.getVerificationStatus())) return false;
        var provider = providers.findById(assignment.getServiceProviderId()).orElse(null);
        if (provider == null || !"ACTIVE".equals(provider.getStatus()) || "UNAVAILABLE".equals(provider.getAvailabilityStatus())) return false;
        if (providerMarkets.findByServiceProviderIdAndStatus(provider.getServiceProviderId(), "ACTIVE")
                .filter(m -> marketId.equals(m.getMarketId())).isEmpty()) return false;
        return selectedOptions.findByAssignmentIdAndOptionId(assignment.getAssignmentId(), option.getOptionId())
                .filter(ProviderServiceOption::canAcceptBookings).isPresent();
    }

    public boolean scheduled(ServiceProviderTypeAssignment assignment, LocalDateTime start, LocalDateTime end) {
        var type = types.findById(assignment.getServiceProviderTypeId()).orElseThrow();
        if (!type.isAvailabilityEnabled()) return true;
        var unavailable = schedules.findAllByAssignmentIdAndScheduleTypeOrderByAvailableDateAsc(assignment.getAssignmentId(), "UNAVAILABLE_DATE");
        var weekly = schedules.findAllByAssignmentIdAndScheduleTypeOrderByDayOfWeekAscStartTimeAsc(assignment.getAssignmentId(), "WEEKLY");
        for (LocalDate day = start.toLocalDate(); day.atStartOfDay().isBefore(end); day = day.plusDays(1)) {
            LocalDate date = day;
            if (unavailable.stream().anyMatch(u -> date.equals(u.getAvailableDate()) && !"INACTIVE".equals(u.getStatus()))) return false;
            if (assignment.isAvailableAnyDay() && assignment.isAvailableAnyTime()) continue;
            LocalDateTime from = start.isAfter(day.atStartOfDay()) ? start : day.atStartOfDay();
            LocalDateTime until = end.isBefore(day.plusDays(1).atStartOfDay()) ? end : day.plusDays(1).atStartOfDay();
            List<Window> windows = new ArrayList<>();
            for (var slot : weekly) {
                if (slot.getDayOfWeek() != day.getDayOfWeek() || !"ACTIVE".equals(slot.getStatus())) continue;
                if (assignment.isAvailableAnyTime() || slot.isAllDay()) windows.add(new Window(day.atStartOfDay(), day.plusDays(1).atStartOfDay()));
                else if (slot.getStartTime() != null && slot.getEndTime() != null) windows.add(new Window(day.atTime(slot.getStartTime()), day.atTime(slot.getEndTime())));
            }
            windows.sort(Comparator.comparing(Window::start));
            LocalDateTime covered = from;
            for (var window : windows) {
                if (window.start().isAfter(covered)) break;
                if (window.end().isAfter(covered)) covered = window.end();
            }
            if (covered.isBefore(until)) return false;
        }
        return true;
    }

    public int remaining(ServiceProviderTypeAssignment assignment, ServiceOption option, Long locationId,
                         Integer locationCapacity, LocalDateTime start, LocalDateTime end, boolean locked) {
        var reserved = locked ? jobs.findLockedReservedOverlaps(assignment.getServiceProviderId(), start, end,
                Set.of("CANCELLED", "COMPLETED", "REJECTED", "EXPIRED", "AWAITING_COMPLETION_CONFIRMATION"))
                : jobs.findReservedOverlaps(assignment.getServiceProviderId(), start, end,
                Set.of("CANCELLED", "COMPLETED", "REJECTED", "EXPIRED", "AWAITING_COMPLETION_CONFIRMATION"));
        for (var job : reserved) {
            if (!assignment.getServiceProviderTypeId().equals(job.getServiceProviderTypeId())
                    && !option.getOptionId().equals(job.getServiceOptionId())) continue;
            boolean exclusive = "ONE_AT_A_TIME".equals(option.getBookingMode())
                    || options.findById(job.getServiceOptionId()).map(o -> "ONE_AT_A_TIME".equals(o.getBookingMode())).orElse(false);
            if (exclusive) return 0;
        }
        int capacity = !"ONE_AT_A_TIME".equals(option.getBookingMode()) && "MANY_CUSTOMERS_ONE_PROVIDER".equals(option.getFulfillmentModel())
                ? (locationCapacity != null ? locationCapacity : option.getDefaultCapacity() == null ? 1 : option.getDefaultCapacity()) : 1;
        var relevant = reserved.stream().filter(j -> option.getOptionId().equals(j.getServiceOptionId()))
                .filter(j -> locationId == null || locationId.equals(j.getProviderLocationId())).toList();
        return Math.max(0, capacity - peakOccupancy(relevant, start, end));
    }

    static int peakOccupancy(List<Job> jobs, LocalDateTime start, LocalDateTime end) {
        TreeMap<LocalDateTime, Integer> events = new TreeMap<>();
        Set<Long> seen = new HashSet<>();
        for (var job : jobs) {
            if (job.getJobId() != null && !seen.add(job.getJobId())) continue;
            if (!job.getStartDatetime().isBefore(end) || !job.getExpectedEndDatetime().isAfter(start)) continue;
            int units = job.getCapacityUsed() == null ? 1 : Math.max(1, job.getCapacityUsed());
            events.merge(job.getStartDatetime().isAfter(start) ? job.getStartDatetime() : start, units, Integer::sum);
            events.merge(job.getExpectedEndDatetime().isBefore(end) ? job.getExpectedEndDatetime() : end, -units, Integer::sum);
        }
        int used = 0, peak = 0;
        for (int delta : events.values()) { used += delta; peak = Math.max(peak, used); }
        return peak;
    }
    @Transactional
    public void lockProviders(List<Long> ids) {
        ids.stream().distinct().sorted().forEach(id -> providers.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found")));
    }
    public static double distance(BigDecimal a, BigDecimal b, BigDecimal c, BigDecimal d) {
        double x = Math.toRadians(c.doubleValue() - a.doubleValue()), y = Math.toRadians(d.doubleValue() - b.doubleValue());
        double h = Math.sin(x/2)*Math.sin(x/2) + Math.cos(Math.toRadians(a.doubleValue()))
                * Math.cos(Math.toRadians(c.doubleValue())) * Math.sin(y/2)*Math.sin(y/2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1-h)));
    }
    private record Window(LocalDateTime start, LocalDateTime end) {}
}
