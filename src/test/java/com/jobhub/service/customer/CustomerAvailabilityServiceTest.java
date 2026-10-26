package com.jobhub.service.customer;

import com.jobhub.entity.job.Job;
import com.jobhub.entity.provider.*;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.market.*;
import com.jobhub.repository.provider.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class CustomerAvailabilityServiceTest {
    @Mock MarketRepository markets;
    @Mock MarketServiceOfferingRepository offerings;
    @Mock ServiceProviderTypeRepository types;
    @Mock ServiceProviderRepository providers;
    @Mock ServiceProviderMarketRepository providerMarkets;
    @Mock ProviderServiceOptionRepository selectedOptions;
    @Mock ServiceProviderAvailabilityRepository schedules;
    @Mock ServiceOptionRepository options;
    @Mock JobRepository jobs;
    @InjectMocks CustomerAvailabilityService service;
    LocalDateTime start = LocalDateTime.of(2030, 1, 7, 8, 0), end = start.plusHours(4);

    Job job(long id, LocalDateTime from, LocalDateTime until, int units) {
        var j = new Job(); j.setJobId(id); j.setServiceProviderTypeId(3L); j.setServiceOptionId(12L);
        j.setStartDatetime(from); j.setExpectedEndDatetime(until); j.setCapacityUsed(units); return j;
    }
    ServiceProviderTypeAssignment assignment() {
        var a = new ServiceProviderTypeAssignment(); a.setAssignmentId(14L); a.setServiceProviderId(15L);
        a.setServiceProviderTypeId(3L); return a;
    }
    ServiceOption option() {
        var o = new ServiceOption(); o.setOptionId(12L); o.setServiceProviderTypeId(3L);
        o.setBookingMode("MANY_AT_A_TIME"); o.setFulfillmentModel("MANY_CUSTOMERS_ONE_PROVIDER"); o.setDefaultCapacity(5); return o;
    }
    ServiceProviderAvailability slot(int from, int until) {
        var s = new ServiceProviderAvailability(); s.setDayOfWeek(start.getDayOfWeek());
        s.setStartTime(LocalTime.of(from, 0)); s.setEndTime(LocalTime.of(until, 0)); s.setStatus("ACTIVE"); return s;
    }
    void schedule(List<ServiceProviderAvailability> slots) {
        var t = new ServiceProviderType(); t.setAvailabilityEnabled(true);
        when(types.findById(3L)).thenReturn(Optional.of(t));
        when(schedules.findAllByAssignmentIdAndScheduleTypeOrderByDayOfWeekAscStartTimeAsc(14L, "WEEKLY")).thenReturn(slots);
    }
    @Test void reviewedOptionIsEligibleWithoutChangingAssignmentActivationStatus() {
        var assignment = assignment();
        assignment.setStatus("ACTIVE"); assignment.setProviderStatus("ACTIVE");
        assignment.setAdminStatus("ACTIVE"); assignment.setVerificationStatus("APPROVED");
        var provider = new ServiceProvider(); provider.setServiceProviderId(15L);
        provider.setStatus("ACTIVE"); provider.setAvailabilityStatus("AVAILABLE");
        when(providers.findById(15L)).thenReturn(Optional.of(provider));
        var market = new ServiceProviderMarket(); market.setMarketId(1L);
        when(providerMarkets.findByServiceProviderIdAndStatus(15L, "ACTIVE")).thenReturn(Optional.of(market));
        var selected = new ProviderServiceOption();
        selected.setProviderStatus("ACTIVE"); selected.setVerificationStatus("APPROVED");
        when(selectedOptions.findByAssignmentIdAndOptionId(14L, 12L)).thenReturn(Optional.of(selected));
        selected.setAdminStatus("APPROVED");
        assertThat(service.qualified(assignment, option(), 1L)).isTrue();
        // Existing ACTIVE rows are still eligible when verification is approved.
        selected.setAdminStatus("ACTIVE");
        assertThat(service.qualified(assignment, option(), 1L)).isTrue();
        for (String status : Arrays.asList("PENDING", "REJECTED", "INACTIVE", null)) {
            selected.setAdminStatus(status);
            assertThat(service.qualified(assignment, option(), 1L)).as("admin status %s", status).isFalse();
        }
        selected.setAdminStatus("APPROVED"); selected.setVerificationStatus("PENDING");
        assertThat(service.qualified(assignment, option(), 1L)).isFalse();
        selected.setVerificationStatus("APPROVED"); selected.setProviderStatus("INACTIVE");
        assertThat(service.qualified(assignment, option(), 1L)).isFalse();
        selected.setProviderStatus("ACTIVE"); assignment.setAdminStatus("INACTIVE");
        assertThat(service.qualified(assignment, option(), 1L)).isFalse();
    }

    @Test void adjacentBookingsDoNotConsumeCapacitySimultaneously() {
        var first = job(1, start, start.plusHours(2), 2);
        var second = job(2, start.plusHours(2), end, 3);
        assertThat(CustomerAvailabilityService.peakOccupancy(List.of(first, second), start, end)).isEqualTo(3);
    }
    @Test void overlappingUnitsAreSummedAndLegacyDuplicateJobsAreDeduplicated() {
        var first = job(1, start, end, 2); var second = job(2, start.plusHours(1), end, 3);
        assertThat(CustomerAvailabilityService.peakOccupancy(List.of(first, first, second), start, end)).isEqualTo(5);
    }
    @Test void touchingOutsideIntervalsDoNotOverlap() {
        assertThat(CustomerAvailabilityService.peakOccupancy(List.of(job(1, start.minusHours(1), start, 2),
                job(2, end, end.plusHours(1), 2)), start, end)).isZero();
    }
    @Test void sharedOptionBookingInAnotherServiceStillReservesProvider() {
        var shared = option();
        shared.setBookingMode("ONE_AT_A_TIME");
        var existing = job(1, start, end, 1);
        existing.setServiceProviderTypeId(7L);
        when(jobs.findReservedOverlaps(eq(15L), eq(start), eq(end), anySet()))
                .thenReturn(List.of(existing));
        assertThat(service.remaining(assignment(), shared, null, null, start, end, false)).isZero();
    }
    @Test void adjoiningScheduleSlotsCoverWholeRequest() {
        schedule(List.of(slot(8, 10), slot(10, 12)));
        assertThat(service.scheduled(assignment(), start, end)).isTrue();
    }
    @Test void waitingForCustomerCompletionConfirmationDoesNotReserveProviderCapacity() {
        when(jobs.findReservedOverlaps(eq(15L),eq(start),eq(end),anySet())).thenAnswer(i -> {
            Set<String> excluded=i.getArgument(3);
            assertThat(excluded).contains("AWAITING_COMPLETION_CONFIRMATION");return List.of();
        });
        when(jobs.findLockedReservedOverlaps(eq(15L),eq(start),eq(end),anySet())).thenAnswer(i -> {
            Set<String> excluded=i.getArgument(3);
            assertThat(excluded).contains("AWAITING_COMPLETION_CONFIRMATION");return List.of();
        });
        assertThat(service.remaining(assignment(),option(),null,null,start,end,false)).isEqualTo(5);
        assertThat(service.remaining(assignment(),option(),null,null,start,end,true)).isEqualTo(5);
    }
    @Test void scheduleGapHidesProvider() {
        schedule(List.of(slot(8, 9), slot(10, 12)));
        assertThat(service.scheduled(assignment(), start, end)).isFalse();
    }
    @Test void unavailableDateOverridesAnyDayAnyTime() {
        schedule(List.of()); var a = assignment(); a.setAvailableAnyDay(true); a.setAvailableAnyTime(true);
        var u = new ServiceProviderAvailability(); u.setAvailableDate(start.toLocalDate()); u.setStatus("UNAVAILABLE");
        when(schedules.findAllByAssignmentIdAndScheduleTypeOrderByAvailableDateAsc(14L, "UNAVAILABLE_DATE")).thenReturn(List.of(u));
        assertThat(service.scheduled(a, start, end)).isFalse();
    }
    @Test void bookingEndingAtMidnightDoesNotRequireNextDayAvailability() {
        schedule(List.of()); var a = assignment(); a.setAvailableAnyTime(true);
        var fullDay = slot(8, 12); fullDay.setAllDay(true);
        when(schedules.findAllByAssignmentIdAndScheduleTypeOrderByDayOfWeekAscStartTimeAsc(14L, "WEEKLY")).thenReturn(List.of(fullDay));
        assertThat(service.scheduled(a, start, start.toLocalDate().plusDays(1).atStartOfDay())).isTrue();
        assertThat(service.scheduled(a, start, start.toLocalDate().plusDays(1).atTime(1, 0))).isFalse();
    }
    @Test void locationCapacityCountsOnlyTheSelectedLocation() {
        var here = job(1, start, end, 1); here.setProviderLocationId(50L);
        var elsewhere = job(2, start, end, 9); elsewhere.setProviderLocationId(51L);
        when(jobs.findReservedOverlaps(eq(15L), eq(start), eq(end), any())).thenReturn(List.of(here, elsewhere));
        when(options.findById(12L)).thenReturn(Optional.of(option()));
        assertThat(service.remaining(assignment(), option(), 50L, 3, start, end, false)).isEqualTo(2);
    }
    @Test void existingExclusiveOptionBlocksAConcurrentDifferentOption() {
        var existing = job(1, start, end, 1); existing.setServiceOptionId(13L);
        var exclusive = option(); exclusive.setBookingMode("ONE_AT_A_TIME");
        when(jobs.findReservedOverlaps(eq(15L), eq(start), eq(end), any())).thenReturn(List.of(existing));
        when(options.findById(13L)).thenReturn(Optional.of(exclusive));
        assertThat(service.remaining(assignment(), option(), null, null, start, end, false)).isZero();
    }
    @Test void finalBookingUsesCurrentLockedInventoryRead() {
        when(jobs.findLockedReservedOverlaps(eq(15L), eq(start), eq(end), any())).thenReturn(List.of());
        assertThat(service.remaining(assignment(), option(), null, null, start, end, true)).isEqualTo(5);
        verify(jobs, never()).findReservedOverlaps(anyLong(), any(), any(), any());
    }
    @Test void exclusiveModeNeverAdvertisesMoreThanOneSlot() {
        var exclusive = option(); exclusive.setBookingMode("ONE_AT_A_TIME");
        when(jobs.findReservedOverlaps(eq(15L), eq(start), eq(end), any())).thenReturn(List.of());
        assertThat(service.remaining(assignment(), exclusive, null, null, start, end, false)).isEqualTo(1);
    }
    @Test void providerLocksUseStableIdOrder() {
        when(providers.lockById(anyLong())).thenReturn(Optional.of(new ServiceProvider()));
        service.lockProviders(List.of(9L, 2L, 9L));
        var order = inOrder(providers); order.verify(providers).lockById(2L); order.verify(providers).lockById(9L);
    }
    @Test void ambiguousDstTimesAreRejectedExplicitly() {
        assertThatThrownBy(() -> CustomerDiscoveryService.requireUnambiguous(ZoneId.of("Europe/London"),
                LocalDateTime.of(2030, 10, 27, 0, 30), LocalDateTime.of(2030, 10, 27, 3, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> CustomerDiscoveryService.requireUnambiguous(ZoneId.of("Asia/Colombo"), start, end)).doesNotThrowAnyException();
    }
}
