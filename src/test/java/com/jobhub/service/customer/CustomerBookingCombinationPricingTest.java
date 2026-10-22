package com.jobhub.service.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.job.CreateBookingRequest;
import com.jobhub.dto.market.PricingConditionRequest;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.job.Job;
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
class CustomerBookingCombinationPricingTest {
    @Mock CustomerRepository customerRepository;
    @Mock JobRepository jobRepository;
    @Mock JobCustomFieldAnswerRepository answerRepository;
    @Mock JobProviderAssignmentRepository jobProviderRepository;
    @Mock ServiceProviderTypeAssignmentRepository assignmentRepository;
    @Mock ProviderServiceOptionRepository providerOptionRepository;
    @Mock ServiceOptionRepository optionRepository;
    @Mock ServiceProviderMarketRepository providerMarketRepository;
    @Mock MarketRepository marketRepository;
    @Mock ServiceCustomFieldRepository customFieldRepository;
    @Mock ServicePricingService pricingService;
    @Mock ProviderSearchFields providerSearchFields;
    @Mock CustomerRequirementsService customerRequirementsService;
    @Mock CustomerAvailabilityService availabilityService;
    @Mock WalletService walletService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks CustomerBookingService service;
    ServiceProviderTypeAssignment assignment;
    List<PricingConditionRequest> selections = List.of(
            new PricingConditionRequest(45L, "CAR"), new PricingConditionRequest(46L, "MALE"));
    CreateBookingRequest request;

    @BeforeEach void setup() {
        var customer = new Customer(); customer.setCustomerId(25L);
        when(customerRepository.findByUserId(5L)).thenReturn(Optional.of(customer));
        var option = new ServiceOption(); option.setOptionId(12L); option.setStatus("ACTIVE");
        option.setServiceProviderTypeId(2L);
        option.setDeliveryModes(Set.of("ONLINE"));
        option.getServiceTypes().add(new ServiceTypeOption(2L, 0, "ACTIVE"));
        when(optionRepository.findByOptionIdAndServiceProviderTypeId(12L, 2L)).thenReturn(Optional.of(option));
        assignment = new ServiceProviderTypeAssignment(); assignment.setAssignmentId(14L);
        assignment.setServiceProviderId(15L); assignment.setServiceProviderTypeId(2L);
        assignment.setStatus("ACTIVE"); assignment.setVerificationStatus("APPROVED");
        assignment.setProviderStatus("ACTIVE"); assignment.setAdminStatus("ACTIVE");
        when(assignmentRepository.findByServiceProviderIdAndServiceProviderTypeId(15L, 2L)).thenReturn(Optional.of(assignment));
        var selected = new ProviderServiceOption(); selected.setProviderStatus("ACTIVE");
        selected.setAdminStatus("ACTIVE"); selected.setVerificationStatus("APPROVED");
        when(providerOptionRepository.findByAssignmentIdAndOptionId(14L, 12L)).thenReturn(Optional.of(selected));
        var marketLink = new ServiceProviderMarket(); marketLink.setMarketId(1L);
        when(providerMarketRepository.findByServiceProviderIdAndStatus(15L, "ACTIVE")).thenReturn(Optional.of(marketLink));
        var market = new Market(); market.setMarketId(1L); market.setDefaultCurrency("LKR"); market.setTimezone("Asia/Colombo");
        when(marketRepository.findById(1L)).thenReturn(Optional.of(market));
        when(availabilityService.qualified(eq(assignment), any(), eq(1L))).thenReturn(true);
        when(availabilityService.scheduled(eq(assignment), any(), any())).thenReturn(true);
        when(availabilityService.remaining(eq(assignment), any(), isNull(), isNull(), any(), any(), anyBoolean())).thenReturn(1);
        var snapshot = new ServicePricingService.RateSnapshot("FIXED", null, new BigDecimal("750"), null, null, null, selections);
        when(pricingService.calculate(eq(1L), eq(2L), eq(12L), eq("TIME_BASED"), any(), any(),
                isNull(), isNull(), isNull(), eq(selections))).thenReturn(
                new ServicePricingService.Price(7L, "LKR", new BigDecimal("750.00"), null, selections, snapshot));
        LocalDateTime start = LocalDateTime.now(java.time.ZoneId.of("Asia/Colombo")).plusDays(1);
        request = new CreateBookingRequest(2L, 12L, 15L, null, "ONLINE", start, start.plusHours(1),
                null, null, null, null, null, null, List.of(), "CASH", selections);
    }

    @Test void quoteChecksSelectionsWithoutSavingBookingOrTakingLocks() {
        var quote = service.quote(5L, request);
        assertThat(quote.expectedAmount()).isEqualByComparingTo("750");
        assertThat(quote.pricingSelections().get(0).get("choiceValue").asText()).isEqualTo("CAR");
        assertThat(quote.pricingSnapshot().get("rate").decimalValue()).isEqualByComparingTo("750");
        verify(providerSearchFields).requirePricingSelections(assignment, 12L, selections);
        verifyNoInteractions(jobRepository, jobProviderRepository, answerRepository, walletService);
        verify(availabilityService, never()).lockProviders(any());
    }

    @Test void linkedEntryUsesTheOriginalRegistrationAnswersAndExactlyTheSameFare() {
        var option = optionRepository.findByOptionIdAndServiceProviderTypeId(12L, 2L).orElseThrow();
        option.getServiceTypes().add(new ServiceTypeOption(3L, 0, "ACTIVE"));
        when(optionRepository.findByOptionIdAndServiceProviderTypeId(12L, 3L)).thenReturn(Optional.of(option));
        var linked = new CreateBookingRequest(3L, 12L, 15L, null, "ONLINE", request.startDatetime(),
                request.expectedEndDatetime(), null, null, null, null, null, null, List.of(), "CASH", selections);
        assertThat(service.quote(5L, linked)).isEqualTo(service.quote(5L, request));
        verify(assignmentRepository, never()).findByServiceProviderIdAndServiceProviderTypeId(15L, 3L);
        verify(providerSearchFields, times(2)).requirePricingSelections(assignment, 12L, selections);
        verifyNoInteractions(jobRepository, walletService);
    }

    @Test void bookingPersistsSelectionsAndAppliedPriceSnapshot() {
        when(jobRepository.save(any())).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0); job.setJobId(100L); return job;
        });
        var response = service.create(5L, request);
        assertThat(response.rateId()).isEqualTo(7L);
        assertThat(response.expectedAmount()).isEqualByComparingTo("750");
        assertThat(response.pricingSelections()).hasSize(2);
        assertThat(response.pricingSnapshot().get("conditions")).hasSize(2);
        verify(availabilityService).lockProviders(List.of(15L));
    }

    @Test void mismatchingProviderPreventsSaving() {
        doThrow(new IllegalArgumentException("Provider does not offer selected choices"))
                .when(providerSearchFields).requirePricingSelections(assignment, 12L, selections);
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(5L, request));
        verify(jobRepository, never()).save(any());
    }
}
