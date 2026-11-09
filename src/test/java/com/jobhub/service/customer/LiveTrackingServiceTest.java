package com.jobhub.service.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.job.LiveTrackingApi.*;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.job.*;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.exception.TooManyRequestsException;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.job.*;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.repository.provider.ServiceProviderTypeAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveTrackingServiceTest {
    @Mock JobRepository jobs;
    @Mock JobProviderTrackingRepository trackings;
    @Mock ProviderLiveLocationRepository locations;
    @Mock JobProviderAssignmentRepository jobAssignments;
    @Mock ServiceOptionRepository options;
    @Mock ServiceProviderRepository providers;
    @Mock ServiceProviderTypeAssignmentRepository assignments;
    @Mock CustomerRepository customers;
    @Mock MarketRepository markets;
    @Mock BookingRequestRepository requests;
    @Mock BookingDispatchEventRepository events;
    @Mock ProviderSearchFields searchFields;
    @Mock MapMarkerResolver markers;
    @Spy ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @InjectMocks LiveTrackingService service;

    ServiceOption option;
    Job job;
    Map<String, JobProviderTracking> saved = new HashMap<>();
    ProviderLiveLocation stored;

    @BeforeEach void setup() {
        option = new ServiceOption(); option.setOptionId(3L); option.setLiveTrackingMode("FULL_TRIP");
        option.setLiveTrackingAutoStartMinutes(30); option.setRouteAverageSpeedKmh(30);
        option.getDeliveryModes().add("PROVIDER_TO_CUSTOMER");
        job = new Job(); job.setJobId(29L); job.setMarketId(1L); job.setCustomerId(4L); job.setServiceProviderTypeId(2L);
        job.setServiceOptionId(3L); job.setAssignedServiceProviderId(18L); job.setDeliveryMode("PROVIDER_TO_CUSTOMER");
        job.setJobStatus("CONFIRMED"); job.setStartDatetime(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        job.setLatitude(new BigDecimal("6.927100")); job.setLongitude(new BigDecimal("79.845000"));
        job.setDestinationLatitude(new BigDecimal("6.874000")); job.setDestinationLongitude(new BigDecimal("79.860000"));
        var market = new Market(); market.setMarketId(1L); market.setTimezone("UTC");
        var provider = new ServiceProvider(); provider.setServiceProviderId(18L); provider.setUserId(26L); provider.setFirstName("E2E"); provider.setLastName("Driver");
        var request = new BookingRequest(); request.setRequestId(78L); request.setUserId(27L);
        var customer = new Customer(); customer.setCustomerId(4L);
        lenient().when(options.findById(3L)).thenReturn(Optional.of(option));
        lenient().when(options.findLockedById(3L)).thenReturn(Optional.of(option));
        lenient().when(markets.findById(1L)).thenReturn(Optional.of(market));
        lenient().when(jobs.findById(29L)).thenAnswer(i -> Optional.of(job));
        lenient().when(jobs.lockPaymentJob(29L)).thenAnswer(i -> Optional.of(job));
        lenient().when(providers.findByUserId(26L)).thenReturn(Optional.of(provider));
        lenient().when(providers.findById(18L)).thenReturn(Optional.of(provider));
        lenient().when(requests.findByJobId(29L)).thenReturn(Optional.of(request));
        lenient().when(customers.findByUserId(27L)).thenReturn(Optional.of(customer));
        lenient().when(jobAssignments.findAllByJobIdOrderByJobProviderAssignmentIdAsc(anyLong())).thenReturn(List.of());
        lenient().when(assignments.findByServiceProviderIdAndServiceProviderTypeId(anyLong(), anyLong())).thenReturn(Optional.empty());
        lenient().when(trackings.findByJobIdAndServiceProviderId(anyLong(), anyLong()))
                .thenAnswer(i -> Optional.ofNullable(saved.get(i.getArgument(0) + ":" + i.getArgument(1))));
        lenient().when(trackings.save(any())).thenAnswer(i -> {
            JobProviderTracking t = i.getArgument(0); saved.put(t.getJobId() + ":" + t.getServiceProviderId(), t); return t;
        });
        lenient().when(trackings.findOpenForProvider(eq(18L), any())).thenAnswer(i -> saved.values().stream()
                .filter(t -> LiveTrackingService.OPEN_JOB_STATUSES.contains(job.getJobStatus())).toList());
        lenient().when(locations.findById(18L)).thenAnswer(i -> Optional.ofNullable(stored));
        lenient().when(locations.save(any())).thenAnswer(i -> stored = i.getArgument(0));
    }

    private LocationUpdate point(String lat, String lng) {
        return new LocationUpdate(new BigDecimal(lat), new BigDecimal(lng), 90.0, 25.0, 8.0, Instant.now());
    }

    private List<String> customerEvents() {
        var captor = ArgumentCaptor.forClass(BookingDispatchEvent.class);
        verify(events, atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream().map(BookingDispatchEvent::getEventType).toList();
    }

    @Test void nowBookingStartsTrackingAutomaticallyOnConfirmation() {
        service.onConfirmed(job);
        assertThat(saved.get("29:18").getStartMode()).isEqualTo("AUTO");
        assertThat(customerEvents()).containsExactly("PROVIDER_EN_ROUTE");
        var state = service.providerState(26L, 29L);
        assertThat(state.sendLocation()).isTrue();
        assertThat(state.canStartJourney()).isFalse();
        assertThat(state.status()).isEqualTo("EN_ROUTE");
    }

    @Test void scheduledBookingWaitsForStartJourneyButton() {
        job.setStartDatetime(LocalDateTime.now(ZoneOffset.UTC).plusHours(20));
        service.onConfirmed(job);
        assertThat(saved).isEmpty();
        var before = service.providerState(26L, 29L);
        assertThat(before.canStartJourney()).isTrue();
        assertThat(before.sendLocation()).isFalse();
        assertThat(before.status()).isEqualTo("NOT_STARTED");
        assertThatThrownBy(() -> service.updateLocation(26L, point("6.9", "79.85"))).isInstanceOf(ConflictException.class);

        var after = service.startJourney(26L, 29L);
        assertThat(after.startMode()).isEqualTo("MANUAL");
        assertThat(after.sendLocation()).isTrue();
        assertThat(customerEvents()).containsExactly("PROVIDER_EN_ROUTE");
        // Pressing again is idempotent and does not notify twice.
        service.startJourney(26L, 29L);
        assertThat(customerEvents()).hasSize(1);
    }

    @Test void startJourneyNeedsAConfirmedBookingWithTrackingEnabled() {
        job.setJobStatus("AWAITING_PAYMENT");
        assertThatThrownBy(() -> service.startJourney(26L, 29L)).isInstanceOf(ConflictException.class);
        job.setJobStatus("CONFIRMED");
        job.setDeliveryMode("CUSTOMER_TO_PROVIDER");
        assertThatThrownBy(() -> service.startJourney(26L, 29L)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("not enabled");
        service.onConfirmed(job);
        assertThat(saved).isEmpty();
    }

    @Test void otherProvidersAndCustomersCannotSeeTheJob() {
        var other = new ServiceProvider(); other.setServiceProviderId(99L);
        when(providers.findByUserId(50L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.startJourney(50L, 29L)).isInstanceOf(ResourceNotFoundException.class);
        var customer = new Customer(); customer.setCustomerId(77L);
        when(customers.findByUserId(51L)).thenReturn(Optional.of(customer));
        assertThatThrownBy(() -> service.customerTracking(51L, 29L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void customerSeesLiveLocationWithEtaToPickupThenDestination() {
        service.onConfirmed(job);
        service.updateLocation(26L, point("6.914700", "79.853000"));
        var view = service.customerTracking(27L, 29L);
        assertThat(view.status()).isEqualTo("EN_ROUTE");
        assertThat(view.target().label()).isEqualTo("PICKUP");
        var driver = view.providers().get(0);
        assertThat(driver.displayName()).isEqualTo("E2E Driver");
        assertThat(driver.location().latitude()).isEqualByComparingTo("6.914700");
        assertThat(driver.distanceKm()).isBetween(1.0, 2.5);
        assertThat(driver.etaMinutes()).isPositive();
        assertThat(driver.stale()).isFalse();

        job.setJobStatus("IN_PROGRESS");
        var trip = service.customerTracking(27L, 29L);
        assertThat(trip.status()).isEqualTo("IN_TRIP");
        assertThat(trip.target().label()).isEqualTo("DESTINATION");
        assertThat(trip.target().navigationUrl()).startsWith("https://www.google.com/maps/dir/?api=1&destination=")
                .contains(trip.target().latitude().toPlainString() + "," + trip.target().longitude().toPlainString());
        assertThat(trip.providers().get(0).location()).isNotNull();

        job.setJobStatus("AWAITING_COMPLETION_CONFIRMATION");
        var ended = service.customerTracking(27L, 29L);
        assertThat(ended.status()).isEqualTo("ENDED");
        assertThat(ended.providers().get(0).location()).isNull();
    }

    @Test void enRouteModeStopsSharingAtArrival() {
        option.setLiveTrackingMode("EN_ROUTE");
        service.onConfirmed(job);
        service.updateLocation(26L, point("6.92", "79.85"));
        var state = service.arrived(26L, 29L);
        assertThat(state.status()).isEqualTo("ARRIVED");
        assertThat(state.sendLocation()).isFalse();
        assertThat(customerEvents()).containsExactly("PROVIDER_EN_ROUTE", "PROVIDER_ARRIVED");
        assertThat(service.customerTracking(27L, 29L).providers().get(0).location()).isNull();
        assertThatThrownBy(() -> service.updateLocation(26L, point("6.92", "79.85"))).isInstanceOf(ConflictException.class);
    }

    @Test void fullTripKeepsSharingAfterArrivalAndDuringTheTrip() {
        service.onConfirmed(job);
        service.arrived(26L, 29L);
        assertThat(service.providerState(26L, 29L).sendLocation()).isTrue();
        job.setJobStatus("IN_PROGRESS");
        var state = service.providerState(26L, 29L);
        assertThat(state.status()).isEqualTo("IN_TRIP");
        assertThat(state.sendLocation()).isTrue();
        assertThat(state.canMarkArrived()).isFalse();
    }

    @Test void locationRecordedBeforeThisJourneyIsNeverShown() {
        service.onConfirmed(job);
        stored = new ProviderLiveLocation(); stored.setServiceProviderId(18L);
        stored.setLatitude(new BigDecimal("7.0")); stored.setLongitude(new BigDecimal("80.0"));
        stored.setRecordedAt(Instant.now().minusSeconds(600)); stored.setReceivedAt(Instant.now().minusSeconds(600));
        assertThat(service.customerTracking(27L, 29L).providers().get(0).location()).isNull();
    }

    @Test void staleLocationIsFlagged() {
        service.onConfirmed(job);
        saved.get("29:18").setJourneyStartedAt(Instant.now().minusSeconds(300));
        stored = new ProviderLiveLocation(); stored.setServiceProviderId(18L);
        stored.setLatitude(new BigDecimal("6.92")); stored.setLongitude(new BigDecimal("79.85"));
        stored.setRecordedAt(Instant.now().minusSeconds(60)); stored.setReceivedAt(Instant.now().minusSeconds(60));
        assertThat(service.customerTracking(27L, 29L).providers().get(0).stale()).isTrue();
    }

    @Test void locationUpdatesAreRateLimitedAndValidated() {
        service.onConfirmed(job);
        var accepted = service.updateLocation(26L, point("6.92", "79.85"));
        assertThat(accepted.sharedWithJobIds()).containsExactly(29L);
        assertThatThrownBy(() -> service.updateLocation(26L, point("6.93", "79.85"))).isInstanceOf(TooManyRequestsException.class);
        var old = new LocationUpdate(new BigDecimal("6.9"), new BigDecimal("79.8"), null, null, null, Instant.now().minusSeconds(600));
        assertThatThrownBy(() -> service.updateLocation(26L, old)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void trackingNeedsProviderToCustomerDelivery() {
        option.getDeliveryModes().clear(); option.getDeliveryModes().add("ONLINE");
        assertThatThrownBy(() -> service.updateSettings(3L, new Settings("FULL_TRIP", 30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.updateSettings(3L, new Settings("NONE", 30)).mode()).isEqualTo("NONE");
    }

    @Test void unsetOptionDefaultsToNoTracking() {
        option.setLiveTrackingMode(null); option.setLiveTrackingAutoStartMinutes(null);
        assertThat(service.settings(3L)).isEqualTo(new Settings("NONE", 30));
        assertThat(service.customerTracking(27L, 29L).status()).isEqualTo("NOT_SUPPORTED");
    }
}
