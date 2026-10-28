package com.jobhub.service.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.customer.CustomerDiscoveryApi.Point;
import com.jobhub.dto.job.BookingRequestsApi.Estimate;
import com.jobhub.dto.job.CreateBookingRequest;
import com.jobhub.dto.market.PricingConditionRequest;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.provider.*;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.job.*;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.provider.*;
import com.jobhub.service.finance.WalletService;
import com.jobhub.service.market.ServicePricingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerBookingEstimateTest {
    @Mock CustomerRepository customerRepository;
    @Mock JobRepository jobRepository;
    @Mock JobCustomFieldAnswerRepository answerRepository;
    @Mock JobProviderAssignmentRepository jobProviderRepository;
    @Mock ServiceProviderTypeAssignmentRepository assignmentRepository;
    @Mock ServiceOptionRepository optionRepository;
    @Mock ServiceProviderMarketRepository providerMarketRepository;
    @Mock MarketRepository marketRepository;
    @Mock ServiceCustomFieldRepository customFieldRepository;
    @Mock ServicePricingService pricingService;
    @Mock CustomerRequirementsService customerRequirementsService;
    @Mock CustomerAvailabilityService availabilityService;
    @Mock WalletService walletService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks CustomerBookingService service;
    List<PricingConditionRequest> selections = List.of(new PricingConditionRequest(45L, "CAR"));
    LocalDateTime start = LocalDateTime.now(java.time.ZoneId.of("Asia/Colombo")).plusDays(1);

    @BeforeEach void setup() {
        var option = new ServiceOption(); option.setOptionId(12L); option.setStatus("ACTIVE");
        option.setServiceProviderTypeId(2L); option.setLocationEnabled(true);
        option.setDeliveryModes(Set.of("ONLINE", "CUSTOMER_TO_PROVIDER", "PROVIDER_TO_CUSTOMER"));
        option.getServiceTypes().add(new ServiceTypeOption(2L, 0, "ACTIVE"));
        lenient().when(optionRepository.findByOptionIdAndServiceProviderTypeId(12L, 2L)).thenReturn(Optional.of(option));
        var market = new Market(); market.setMarketId(1L); market.setDefaultCurrency("LKR"); market.setTimezone("Asia/Colombo");
        lenient().when(marketRepository.findById(1L)).thenReturn(Optional.of(market));
        var snapshot = new ServicePricingService.RateSnapshot("FIXED", null, new BigDecimal("750"), null, null, null, selections);
        lenient().when(pricingService.calculate(eq(1L), eq(2L), eq(12L), eq("TIME_BASED"), any(), any(), isNull(), any(), any(), eq(selections)))
                .thenReturn(new ServicePricingService.Price(7L, "LKR", new BigDecimal("750.00"), null, selections, snapshot));
    }

    private CreateBookingRequest booking(Long providerId, String mode, CreateBookingRequest.MeetingPoint meetingPoint) {
        return new CreateBookingRequest(2L, 12L, providerId, null, mode, start, start.plusHours(2),
                null, null, null, null, meetingPoint, null, null, "CARD", selections);
    }

    @Test void estimatesWithoutProviderAnswersOrPersistence() {
        var estimate = service.estimate(new Estimate(1L, booking(null, "ONLINE", null), null));
        assertThat(estimate.expectedAmount()).isEqualByComparingTo("750");
        assertThat(estimate.currencyCode()).isEqualTo("LKR");
        verify(availabilityService).requireOffering(1L, 2L);
        verifyNoInteractions(customerRepository, jobRepository, answerRepository, jobProviderRepository,
                assignmentRepository, providerMarketRepository, customFieldRepository, walletService);
        verify(availabilityService, never()).lockProviders(any());
    }

    @Test void pricesAreaRatesAtTheCustomerPoint() {
        var meeting = new CreateBookingRequest.MeetingPoint("Home", "1 Main St", null, null, null, null, null, null,
                new BigDecimal("6.9"), new BigDecimal("79.8"), null);
        service.estimate(new Estimate(1L, booking(null, "PROVIDER_TO_CUSTOMER", meeting), null));
        verify(pricingService).calculate(eq(1L), eq(2L), eq(12L), eq("TIME_BASED"), any(), any(), isNull(),
                eq(new BigDecimal("6.9")), eq(new BigDecimal("79.8")), eq(selections));
        service.estimate(new Estimate(1L, booking(null, "CUSTOMER_TO_PROVIDER", null),
                new Point(new BigDecimal("7.1"), new BigDecimal("80.1"))));
        verify(pricingService).calculate(eq(1L), eq(2L), eq(12L), eq("TIME_BASED"), any(), any(), isNull(),
                eq(new BigDecimal("7.1")), eq(new BigDecimal("80.1")), eq(selections));
    }

    @Test void rejectsProviderMissingMeetingPointAndPastTime() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.estimate(new Estimate(1L, booking(15L, "ONLINE", null), null)))
                .withMessageContaining("does not take a provider");
        assertThatIllegalArgumentException().isThrownBy(() ->
                service.estimate(new Estimate(1L, booking(null, "PROVIDER_TO_CUSTOMER", null), null)))
                .withMessage("Customer meeting point is required");
        start = start.minusDays(2);
        assertThatIllegalArgumentException().isThrownBy(() -> service.estimate(new Estimate(1L, booking(null, "ONLINE", null), null)))
                .withMessageContaining("future interval");
        verifyNoInteractions(jobRepository);
    }
}
