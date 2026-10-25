package com.jobhub.service.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.customer.CustomerDiscoveryApi.*;
import com.jobhub.dto.job.*;
import com.jobhub.dto.job.BookingRequestsApi.*;
import com.jobhub.entity.auth.User;
import com.jobhub.entity.job.*;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.*;
import com.jobhub.payments.PayHereBookingCheckout;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.job.*;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.provider.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class BookingRequestServiceTest {
    @Mock BookingRequestRepository requests;
    @Mock BookingOfferRepository offers;
    @Mock BookingRequestPolicyRepository policies;
    @Mock BookingDispatchEventRepository events;
    @Mock BookingPaymentNoticeRepository notices;
    @Mock ServiceOptionRepository options;
    @Mock ServiceProviderRepository providers;
    @Mock MarketRepository markets;
    @Mock UserRepository users;
    @Mock JobRepository jobs;
    @Mock CustomerDiscoveryService discovery;
    @Mock CustomerBookingService bookings;
    @Mock PayHereBookingCheckout checkout;
    @Mock com.jobhub.service.audit.AuditService audit;
    @Mock com.jobhub.service.finance.WalletService wallets;
    @Spy BookingCodeCipher codes=new BookingCodeCipher(Base64.getEncoder().encodeToString(new byte[32]));
    @Spy ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    @InjectMocks BookingRequestService service;
    BookingRequest request;
    ServiceOption option;
    BookingRequestPolicy policy;
    Job job;
    Create input;
    List<BookingOffer> stored=new ArrayList<>();
    LocalDateTime start=LocalDateTime.now(ZoneOffset.UTC).plusDays(2).withNano(0);
    @BeforeEach void setup() {
        lenient().when(requests.lockByJobId(70L)).thenAnswer(i -> Optional.of(request));
        option=new ServiceOption();option.setOptionId(12L);option.setServiceProviderTypeId(3L);option.setStatus("ACTIVE");
        option.getServiceTypes().add(new ServiceTypeOption(3L,0,"ACTIVE"));option.setProviderSelectionMode("OPEN_REQUEST");
        policy=new BookingRequestPolicy();policy.setOptionId(12L);policy.setEnabled(true);
        var market=new Market();market.setMarketId(1L);market.setTimezone("UTC");
        var provider=new ServiceProvider();provider.setServiceProviderId(15L);provider.setUserId(9L);
        var booking=new CreateBookingRequest(3L,12L,null,null,"ONLINE",start,start.plusHours(1),null,null,null,null,null,null,List.of(),"CARD",null);
        input=new Create(1L,booking,null,null,null,null,null);
        job=new Job();job.setJobId(70L);job.setMarketId(1L);job.setAssignedServiceProviderId(15L);job.setExpectedAmount(new BigDecimal("1000.00"));
        job.setCurrencyCode("LKR");job.setStartDatetime(start);job.setExpectedEndDatetime(start.plusHours(1));job.setJobStatus("AWAITING_PAYMENT");job.setPaymentStatus("PENDING");
        lenient().when(users.lockById(5L)).thenReturn(Optional.of(new User()));
        lenient().when(options.findByOptionIdAndServiceProviderTypeId(12L,3L)).thenReturn(Optional.of(option));
        lenient().when(policies.findById(12L)).thenReturn(Optional.of(policy));
        lenient().when(markets.findById(1L)).thenReturn(Optional.of(market));
        lenient().when(providers.findById(15L)).thenReturn(Optional.of(provider));
        lenient().when(providers.findByUserId(9L)).thenReturn(Optional.of(provider));
        var candidate=new Candidate(15L,20L,12L,null,"Provider",null,null,null,null,null,null,1,List.of(),Map.of(),null);
        var results=new Results(Instant.now(),1L,"UTC",null,null,"OPEN_REQUEST",1,"AVAILABLE",1,0,100,false,List.of(candidate));
        lenient().when(discovery.search(eq(5L),any())).thenReturn(results);
        lenient().when(discovery.searchForProvider(eq(5L),any(),eq(15L))).thenReturn(results);
        lenient().when(bookings.quote(eq(5L),any())).thenReturn(new BookingQuoteResponse(1L,"LKR",job.getExpectedAmount(),start,start.plusHours(1),null,null,null));
        lenient().when(bookings.reserveCardRequest(eq(5L),any())).thenReturn(job);
        lenient().when(requests.saveAndFlush(any())).thenAnswer(i -> {request=i.getArgument(0);request.setRequestId(1L);return request;});
        lenient().when(requests.lockById(1L)).thenAnswer(i -> Optional.ofNullable(request));
        lenient().when(requests.findByJobId(70L)).thenAnswer(i -> Optional.ofNullable(request));
        lenient().when(requests.findByPaymentOrderId(anyString())).thenAnswer(i -> Optional.ofNullable(request));
        lenient().when(jobs.lockPaymentJob(70L)).thenReturn(Optional.of(job));
        lenient().when(jobs.findById(70L)).thenReturn(Optional.of(job));
        lenient().when(checkout.fundingMode()).thenReturn("PAYHERE_SANDBOX");
        lenient().when(wallets.settleCardBooking(5L,70L)).thenAnswer(i -> {
            job.setPaymentStatus("SETTLED");request.setSettlementStatus("POSTED");request.setSettlementOperationId(66L);
            return new com.jobhub.dto.customer.WalletApi.Operation(66L,"CARD_BOOKING_SETTLE",70L,"LKR",job.getExpectedAmount(),"POSTED");
        });
        lenient().when(offers.saveAndFlush(any())).thenAnswer(i -> {BookingOffer o=i.getArgument(0);o.setOfferId((long)stored.size()+1);stored.add(o);return o;});
        lenient().when(offers.findAllByRequestIdOrderByOfferIdAsc(1L)).thenAnswer(i -> stored);
        lenient().when(offers.findById(anyLong())).thenAnswer(i -> stored.stream().filter(o -> o.getOfferId().equals(i.getArgument(0))).findFirst());
    }
    void create(){service.create(5L,"key",input);}
    void accepted(){create();service.accept(9L,1L);}
    Confirm contact(){return new Confirm("VISA","A","B","a@example.com","0771234567","Address","Colombo","Sri Lanka");}
    void paying(){accepted();when(checkout.checkout(any(),any(),any())).thenReturn(new Checkout("https://sandbox.payhere.lk/pay/checkout",Map.of(),"PAYMENT_PENDING"));service.confirm(5L,1L,contact());}
    Map<String,String> notification(String status,String paymentId){return Map.of("order_id",request.getPaymentOrderId(),"payhere_amount","1000.00","payhere_currency","LKR","status_code",status,"payment_id",paymentId,"method","VISA");}

    @Test void openDispatchValidatesBeforeOfferingWithoutCreatingOrChargingJob(){
        var result=service.create(5L,"key",input);
        assertThat(result.status()).isEqualTo("MATCHING");assertThat(stored).hasSize(1);
        assertThat(result.expectedAmount()).isEqualByComparingTo("1000");
        verify(bookings).validateRequestFields(5L,input.booking());verify(bookings,never()).reserveCardRequest(any(),any());
        verify(checkout,never()).checkout(any(),any(),any());
    }
    @Test void invalidRequiredFieldsNeverCreateRequestOrOffers(){
        doThrow(new IllegalArgumentException("Ward is required")).when(bookings).validateRequestFields(any(),any());
        assertThatIllegalArgumentException().isThrownBy(this::create);verify(requests,never()).saveAndFlush(any());verifyNoInteractions(offers);
    }
    @Test void directDispatchTargetsSelectedProviderBeforePagination(){
        option.setProviderSelectionMode("DIRECT_REQUEST");var b=input.booking();
        input=new Create(1L,new CreateBookingRequest(3L,12L,15L,null,"ONLINE",b.startDatetime(),b.expectedEndDatetime(),null,null,null,null,null,null,List.of(),"CARD",null),null,null,null,null,null);
        create();assertThat(request.getSelectionMode()).isEqualTo("DIRECT_REQUEST");verify(discovery).searchForProvider(eq(5L),any(),eq(15L));verify(discovery,never()).search(any(),any());
        service.decline(9L,1L);assertThat(request.getStatus()).isEqualTo("UNMATCHED");assertThat(stored).hasSize(1);
    }
    @Test void duplicateCreateReturnsExistingRequestAndChangedPayloadConflicts(){
        create();when(requests.findByUserIdAndIdempotencyKey(5L,"key")).thenReturn(Optional.of(request));
        assertThat(service.create(5L,"key",input).requestId()).isEqualTo(1L);
        var changed=new Create(1L,input.booking(),null,new BigDecimal("20"),null,null,null);
        assertThatThrownBy(() -> service.create(5L,"key",changed)).isInstanceOf(ConflictException.class);
        verify(requests,times(1)).saveAndFlush(any());
    }
    @Test void minimumNoticeAndUnsupportedTeamPreventDispatch(){
        policy.setMinimumNoticeMinutes(10000);assertThatIllegalArgumentException().isThrownBy(this::create);
        policy.setMinimumNoticeMinutes(120);option.setFulfillmentModel("ONE_CUSTOMER_MANY_PROVIDERS");
        assertThatIllegalArgumentException().isThrownBy(this::create);verify(requests,never()).saveAndFlush(any());
    }
    @Test void acceptingReservesCapacityAndWaitsForCustomerWithoutPayment(){
        accepted();assertThat(request.getStatus()).isEqualTo("AWAITING_CUSTOMER_CONFIRMATION");assertThat(request.getJobId()).isEqualTo(70L);
        assertThat(stored.get(0).getStatus()).isEqualTo("ACCEPTED");assertThat(request.getConfirmationExpiresAt()).isAfter(Instant.now());
        service.accept(9L,1L);verify(bookings,times(1)).reserveCardRequest(any(),any());verify(checkout,never()).checkout(any(),any(),any());
    }
    @Test void expiredOfferCannotReserveAndWorkerExpiresIt(){
        create();stored.get(0).setExpiresAt(Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.accept(9L,1L)).isInstanceOf(ConflictException.class);
        service.expire(1L);assertThat(request.getStatus()).isEqualTo("UNMATCHED");verify(bookings,never()).reserveCardRequest(any(),any());
    }
    @Test void expiredConfirmationReleasesPendingJob(){
        accepted();request.setConfirmationExpiresAt(Instant.now().minusSeconds(1));service.expire(1L);
        assertThat(request.getStatus()).isEqualTo("EXPIRED");assertThat(job.getJobStatus()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> service.confirm(5L,1L,contact())).isInstanceOf(ConflictException.class);
    }
    @Test void customerOwnershipAndOfferOwnershipAreEnforced(){
        create();assertThatThrownBy(() -> service.get(99L,1L)).isInstanceOf(ResourceNotFoundException.class);
        var stranger=new ServiceProvider();stranger.setServiceProviderId(22L);when(providers.findByUserId(99L)).thenReturn(Optional.of(stranger));
        assertThatThrownBy(() -> service.accept(99L,1L)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void confirmReturnsHostedCheckoutOnceAndNeverConfirmsFromClient(){
        paying();service.confirm(5L,1L,contact());
        verify(checkout,times(1)).checkout(any(),any(),any());assertThat(request.getStatus()).isEqualTo("PAYMENT_PENDING");
        assertThat(job.getJobStatus()).isEqualTo("AWAITING_PAYMENT");assertThat(request.getStartCode()).isNull();
        assertThatThrownBy(() -> service.cancel(5L,1L)).isInstanceOf(ConflictException.class);
    }
    @Test void verifiedSuccessConfirmsAndStoresProtectedStartCode(){
        paying();service.notification(notification("2","pay-1"));
        assertThat(request.getStatus()).isEqualTo("CONFIRMED");assertThat(job.getPaymentStatus()).isEqualTo("PAID");
        assertThat(codes.decrypt(request.getStartCode())).matches("[0-9]{6}");
        verify(checkout).verify(any());verify(notices).saveAndFlush(any());
    }
    @Test void replayedNotificationDoesNotRegenerateCodesOrTransitionAgain(){
        paying();service.notification(notification("2","pay-1"));String protectedCode=request.getStartCode();
        var previous=new BookingPaymentNotice();previous.setRequestId(1L);
        when(notices.findByPaymentIdAndStatusCode("pay-1","2")).thenReturn(Optional.of(previous));
        service.notification(notification("2","pay-1"));assertThat(request.getStartCode()).isEqualTo(protectedCode);
        verify(notices,times(1)).saveAndFlush(any());
    }
    @Test void wrongAmountCannotConfirmAndFailureCannotUndoPaidBooking(){
        paying();var bad=new HashMap<>(notification("2","pay-1"));bad.put("payhere_amount","1.00");
        assertThatIllegalArgumentException().isThrownBy(() -> service.notification(bad));assertThat(request.getStatus()).isEqualTo("PAYMENT_PENDING");
        service.notification(notification("2","pay-1"));service.notification(notification("-2","pay-1"));
        assertThat(request.getStatus()).isEqualTo("CONFIRMED");assertThat(job.getPaymentStatus()).isEqualTo("PAID");
    }
    @Test void unknownPaymentRetainsCapacityAndLateSuccessCanResolveIt(){
        paying();request.setPaymentStartedAt(Instant.now().minusSeconds(1000));service.expire(1L);
        assertThat(request.getStatus()).isEqualTo("PAYMENT_RECONCILIATION");assertThat(job.getJobStatus()).isEqualTo("AWAITING_PAYMENT");
        service.notification(notification("2","pay-1"));assertThat(request.getStatus()).isEqualTo("CONFIRMED");
    }
    @Test void successAfterFailedPaymentDoesNotConfirmReleasedCapacity(){
        paying();service.notification(notification("-2","pay-1"));assertThat(job.getJobStatus()).isEqualTo("CANCELLED");
        service.notification(notification("2","pay-1"));assertThat(request.getStatus()).isEqualTo("PAYMENT_REVIEW");
        assertThat(request.getPaymentStatus()).isEqualTo("REFUND_REVIEW");assertThat(job.getJobStatus()).isEqualTo("CANCELLED");
    }
    @Test void chargebackIsNotOverwrittenByLaterSuccess(){
        paying();service.notification(notification("2","pay-1"));service.notification(notification("-3","pay-1"));
        service.notification(notification("2","pay-1"));assertThat(job.getPaymentStatus()).isEqualTo("CHARGEDBACK");
    }
    @Test void codeAttemptsPersistAndLockAtFiveWithoutStateChange(){
        paying();service.notification(notification("2","pay-1"));job.setStartDatetime(LocalDateTime.now(ZoneOffset.UTC));
        String wrong=codes.decrypt(request.getStartCode()).equals("000000")?"000001":"000000";
        for(int i=0;i<5;i++)assertThat(service.verifyStartCode(9L,70L,wrong).successful()).isFalse();
        assertThat(request.getStartAttempts()).isEqualTo(5);assertThat(service.verifyStartCode(9L,70L,codes.decrypt(request.getStartCode())).status()).isEqualTo("CODE_LOCKED");
        assertThat(request.getStatus()).isEqualTo("CONFIRMED");
    }
    @Test void startNeedsCodeButCompletionNeedsOnlyOwningCustomerConfirmation(){
        paying();service.notification(notification("2","pay-1"));job.setStartDatetime(LocalDateTime.now(ZoneOffset.UTC));
        String startCode=service.customerCodes(5L,70L).code();
        assertThatThrownBy(() -> service.requestCompletion(9L,70L)).isInstanceOf(ConflictException.class);
        assertThat(service.verifyStartCode(9L,70L,startCode).successful()).isTrue();assertThat(job.getActualStartDatetime()).isNotNull();
        assertThatThrownBy(() -> service.customerCodes(5L,70L)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.confirmCompletion(5L,70L)).isInstanceOf(ConflictException.class);
        assertThat(service.requestCompletion(9L,70L).status()).isEqualTo("AWAITING_COMPLETION_CONFIRMATION");
        var endedAt=job.getActualEndDatetime();assertThat(endedAt).isNotNull();
        service.requestCompletion(9L,70L);assertThat(job.getActualEndDatetime()).isEqualTo(endedAt);
        assertThat(service.confirmCompletion(5L,70L).status()).isEqualTo("COMPLETED");
        assertThat(service.confirmCompletion(5L,70L).successful()).isTrue();
        assertThat(service.requestCompletion(9L,70L).status()).isEqualTo("COMPLETED");
        assertThat(job.getActualEndDatetime()).isEqualTo(endedAt);
        assertThat(job.getPaymentStatus()).isEqualTo("SETTLED");
        service.notification(notification("2","pay-1"));
        assertThat(job.getPaymentStatus()).isEqualTo("SETTLED");
        verify(events,times(2)).save(argThat(e -> "COMPLETION_REQUESTED".equals(e.getEventType())));
        verify(events,times(2)).save(argThat(e -> "BOOKING_COMPLETED".equals(e.getEventType())));
    }
    @Test void anotherCustomerOrProviderCannotCompleteSomeoneElsesService(){
        paying();service.notification(notification("2","pay-1"));
        job.setStartDatetime(LocalDateTime.now(ZoneOffset.UTC));service.verifyStartCode(9L,70L,codes.decrypt(request.getStartCode()));
        var stranger=new ServiceProvider();stranger.setServiceProviderId(22L);when(providers.findByUserId(99L)).thenReturn(Optional.of(stranger));
        assertThatThrownBy(() -> service.requestCompletion(99L,70L)).isInstanceOf(ResourceNotFoundException.class);
        service.requestCompletion(9L,70L);
        assertThatThrownBy(() -> service.confirmCompletion(99L,70L)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(request.getStatus()).isEqualTo("AWAITING_COMPLETION_CONFIRMATION");
        assertThatThrownBy(() -> service.resetCode(100L,70L,"No completion code should be generated")).isInstanceOf(ConflictException.class);
    }
    @Test void paidStartCodeCannotBeReadBeforeWindowOrByAnotherCustomer(){
        paying();service.notification(notification("2","pay-1"));
        assertThatThrownBy(() -> service.customerCodes(5L,70L)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.customerCodes(99L,70L)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void adminResetReplacesLockedCodeWithoutBypassingCustomerVerification(){
        paying();service.notification(notification("2","pay-1"));String old=request.getStartCode();request.setStartAttempts(5);
        service.resetCode(100L,70L,"Customer identified via support ticket 123");
        assertThat(request.getStartAttempts()).isZero();assertThat(request.getStartCode()).isNotEqualTo(old);
        assertThat(request.getStatus()).isEqualTo("CONFIRMED");verify(audit).record(eq(100L),eq("BOOKING_CODE_RESET"),anyString(),eq(1L),isNull(),any());
    }
    @Test void manualUnpaidReviewReleasesCapacityButRecordedSuccessBlocksIt(){
        paying();when(notices.existsByRequestIdAndStatusCode(1L,"2")).thenReturn(true);
        assertThatThrownBy(() -> service.resolveUnpaid(100L,1L,"Merchant dashboard checked")).isInstanceOf(ConflictException.class);
        when(notices.existsByRequestIdAndStatusCode(1L,"2")).thenReturn(false);
        service.resolveUnpaid(100L,1L,"Merchant dashboard order verified unpaid");
        assertThat(job.getJobStatus()).isEqualTo("CANCELLED");assertThat(request.getStatus()).isEqualTo("PAYMENT_FAILED");
        verify(audit).record(eq(100L),eq("BOOKING_PAYMENT_REVIEWED_UNPAID"),anyString(),eq(1L),any(),any());
    }
    @Test void offeredPriceChangeDoesNotBecomeCustomerPayment(){
        create();job.setExpectedAmount(new BigDecimal("1500"));
        assertThatThrownBy(() -> service.accept(9L,1L)).isInstanceOf(ConflictException.class);
        assertThat(request.getJobId()).isNull();assertThat(stored.get(0).getStatus()).isEqualTo("PENDING");
        verify(checkout,never()).checkout(any(),any(),any());
    }
    @Test void commissionChangeWithUnchangedFareCannotSilentlyReduceProviderEarnings(){
        create();job.setPlatformFeePercentage(new BigDecimal("10"));job.setPlatformFee(new BigDecimal("100"));job.setProviderEarnings(new BigDecimal("900"));
        assertThatThrownBy(() -> service.accept(9L,1L)).isInstanceOf(ConflictException.class);
        assertThat(request.getJobId()).isNull();assertThat(stored.get(0).getStatus()).isEqualTo("PENDING");
        verify(checkout,never()).checkout(any(),any(),any());
    }
}
