package com.jobhub.service.customer;

import com.jobhub.dto.customer.CustomerDiscoveryApi.*;
import com.jobhub.dto.customer.CustomerRequirementsApi.*;
import com.jobhub.dto.provider.ProviderTypeResponse;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.*;
import com.jobhub.repository.provider.*;
import com.jobhub.service.provider.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class CustomerDiscoveryServiceTest {
    @Mock CustomerProfileService profiles;
    @Mock CustomerRequirementsService requirements;
    @Mock CustomerAvailabilityService availability;
    @Mock ProviderTypeService types;
    @Mock ServiceOptionService optionService;
    @Mock ServiceCustomFieldService fields;
    @Mock ServiceOptionRepository options;
    @Mock ServiceProviderTypeAssignmentRepository assignments;
    @Mock ServiceProviderRepository providers;
    @Mock ServiceProviderServiceAreaRepository areas;
    @Mock ProviderServiceLanguageRepository languages;
    @Mock ProviderServiceSkillRepository skills;
    @Mock ServiceTypeSkillRepository configuredSkills;
    @Mock ProviderSearchFields providerSearchFields;
    @InjectMocks CustomerDiscoveryService service;
    ServiceOption option;
    ServiceProviderTypeAssignment assignment;

    @BeforeEach void setup() {
        lenient().when(providerSearchFields.matches(anyMap(), nullable(List.class))).thenReturn(true);
        option = new ServiceOption(); option.setOptionId(12L); option.setServiceProviderTypeId(3L);
        option.setStatus("ACTIVE"); option.setLocationEnabled(true); option.setDeliveryModes(Set.of("PROVIDER_TO_CUSTOMER", "CUSTOMER_TO_PROVIDER", "ONLINE"));
        var market = new Market(); market.setMarketId(1L); market.setTimezone("Asia/Colombo");
        lenient().when(availability.market(1L)).thenReturn(market);
        lenient().when(options.findByOptionIdAndServiceProviderTypeId(12L, 3L)).thenReturn(Optional.of(option));
        assignment = new ServiceProviderTypeAssignment(); assignment.setAssignmentId(14L); assignment.setServiceProviderId(15L);
        lenient().when(assignments.findAllByServiceProviderTypeId(3L)).thenReturn(List.of(assignment));
        lenient().when(availability.qualified(assignment, option, 1L)).thenReturn(true);
        lenient().when(availability.scheduled(eq(assignment), any(), any())).thenReturn(true);
        var provider = new ServiceProvider(); provider.setServiceProviderId(15L); provider.setFirstName("Jane"); provider.setLastName("Doe");
        lenient().when(providers.findById(15L)).thenReturn(Optional.of(provider));
        lenient().when(availability.remaining(eq(assignment), eq(option), any(), any(), any(), any(), eq(false))).thenReturn(1);
    }
    Search search(String mode, Point location) {
        return new Search(1L, 3L, 12L, mode, OffsetDateTime.parse("2030-01-07T08:00:00+05:30"),
                OffsetDateTime.parse("2030-01-07T10:00:00+05:30"), location, null, null, null, List.of(), List.of(), 0, 20);
    }
    Point point() { return new Point(new BigDecimal("6.9271"), new BigDecimal("79.8612")); }
    ServiceProviderServiceArea area() {
        var a = new ServiceProviderServiceArea(); a.setServiceAreaId(50L); a.setMarketId(1L); a.setStatus("ACTIVE");
        a.setLatitude(new BigDecimal("6.9271")); a.setLongitude(new BigDecimal("79.8612")); a.setRadiusKm(BigDecimal.TEN); a.setCapacity(3); return a;
    }
    @Test void ineligibleSearchStopsBeforeReadingProviderCandidates() {
        doThrow(new CustomerEligibilityException(new Eligibility(false, List.of(new Reason("MISSING", "NIC", "Upload NIC")))))
                .when(requirements).requireEligible(25L, 3L, 12L);
        assertThatThrownBy(() -> service.search(25L, search("ONLINE", null))).isInstanceOf(CustomerEligibilityException.class);
        verifyNoInteractions(assignments);
    }
    @Test void onlineResultsDoNotExposePhysicalCoordinates() {
        var result = service.search(25L, search("ONLINE", null));
        assertThat(result.status()).isEqualTo("AVAILABLE");
        assertThat(result.candidates().get(0).mapPoint()).isNull();
    }
    @Test void fullProvidersDisappearFromResults() {
        when(availability.remaining(eq(assignment), eq(option), any(), any(), any(), any(), eq(false))).thenReturn(0);
        assertThat(service.search(25L, search("ONLINE", null)).candidates()).isEmpty();
    }
    @Test void dynamicFiltersExcludeCandidatesBeforePagination() {
        when(providerSearchFields.matches(anyMap(), nullable(List.class))).thenReturn(false);
        var result = service.search(25L, search("ONLINE", null));
        assertThat(result.totalCandidates()).isZero();
        assertThat(result.candidates()).isEmpty();
    }
    @Test void scheduleUnavailableProvidersAreNotReturned() {
        when(availability.scheduled(eq(assignment), any(), any())).thenReturn(false);
        assertThat(service.search(25L, search("ONLINE", null)).status()).isEqualTo("NO_AVAILABLE_PROVIDERS");
    }
    @Test void travellingProviderCoordinatesAreApproximateAndNotLive() {
        when(areas.findAllByAssignmentIdAndOptionIdAndDeliveryModeOrderByLocationNameAsc(14L, 12L, "PROVIDER_TO_CUSTOMER"))
                .thenReturn(List.of(area()));
        var candidate = service.search(25L, search("PROVIDER_TO_CUSTOMER", point())).candidates().get(0);
        assertThat(candidate.locationLabel()).isEqualTo("APPROXIMATE_SERVICE_AREA");
        assertThat(candidate.mapPoint().latitude()).isEqualByComparingTo("6.93");
        assertThat(candidate.providerLocationId()).isNull();
    }
    @Test void fixedBusinessLocationsExposeSelectableLocationId() {
        when(areas.findAllByAssignmentIdAndOptionIdAndDeliveryModeOrderByLocationNameAsc(14L, 12L, "CUSTOMER_TO_PROVIDER"))
                .thenReturn(List.of(area()));
        var candidate = service.search(25L, search("CUSTOMER_TO_PROVIDER", point())).candidates().get(0);
        assertThat(candidate.providerLocationId()).isEqualTo(50L);
        assertThat(candidate.locationLabel()).isEqualTo("PUBLIC_SERVICE_LOCATION");
    }
    @Test void inactiveAndOutOfMarketLocationsAreExcluded() {
        var inactive = area(); inactive.setStatus("INACTIVE"); var elsewhere = area(); elsewhere.setMarketId(2L);
        when(areas.findAllByAssignmentIdAndOptionIdAndDeliveryModeOrderByLocationNameAsc(14L, 12L, "CUSTOMER_TO_PROVIDER"))
                .thenReturn(List.of(inactive, elsewhere));
        assertThat(service.search(25L, search("CUSTOMER_TO_PROVIDER", point())).candidates()).isEmpty();
    }
    @Test void teamRequiresEnoughDistinctProviders() {
        option.setFulfillmentModel("ONE_CUSTOMER_MANY_PROVIDERS"); option.setRequiredProviderCount(2);
        assertThat(service.search(25L, search("ONLINE", null)).status()).isEqualTo("NO_AVAILABLE_PROVIDERS");
    }
    @Test void restrictedServiceRemainsVisibleWithAnApplicationAction() {
        when(types.listActiveTypes()).thenReturn(List.of(new ProviderTypeResponse(3L, "Nursing", null, null,
                "ACTIVE", null, null, List.of(), List.of())));
        when(requirements.eligibility(25L, 3L, null)).thenReturn(new Eligibility(false, List.of(new Reason("MISSING", "NIC", "Upload NIC"))));
        var result = service.services(25L, 1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).access().action()).isEqualTo("COMPLETE_REQUIREMENTS");
    }
    @Test void languagePreferenceFiltersNonMatchingProviders() {
        var base = search("ONLINE", null);
        var request = new Search(base.marketId(), base.serviceTypeId(), base.optionId(), base.deliveryMode(),
                base.startAt(), base.endAt(), null, null, null, null, List.of("TAMIL"), List.of(), 0, 20);
        assertThat(service.search(25L, request).candidates()).isEmpty();
    }
}
