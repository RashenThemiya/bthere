package com.jobhub.service.admin;

import com.jobhub.dto.admin.BookingMonitorApi.*;
import com.jobhub.entity.job.*;
import com.jobhub.entity.finance.*;
import com.jobhub.repository.job.*;
import com.jobhub.repository.finance.*;
import com.jobhub.service.customer.CustomerBookingService;
import com.jobhub.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingMonitorPersistenceTest {
    static SessionFactory factory;
    EntityManager em;BookingMonitorService service;
    BookingRequest request,other;Job job;BookingOffer offer;WalletOperation operation;
    @BeforeAll static void factory() {
        var c=new Configuration().setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
            .setProperty("hibernate.connection.driver_class","org.h2.Driver")
            .setProperty("hibernate.connection.url","jdbc:h2:mem:"+UUID.randomUUID())
            .setProperty("hibernate.hbm2ddl.auto","create-drop");
        for(var type:List.of(BookingRequest.class,BookingOffer.class,BookingPaymentNotice.class,BookingDispatchEvent.class,
            Job.class,JobProviderAssignment.class,WalletOperation.class,WalletLedgerEntry.class))c.addAnnotatedClass(type);
        factory=c.buildSessionFactory();
    }
    @AfterAll static void closeFactory(){factory.close();}
    @BeforeEach void setup() {
        em=factory.createEntityManager();em.getTransaction().begin();
        var repos=new JpaRepositoryFactory(em);
        service=new BookingMonitorService(repos.getRepository(BookingRequestRepository.class),repos.getRepository(JobRepository.class),
            repos.getRepository(BookingOfferRepository.class),repos.getRepository(BookingPaymentNoticeRepository.class),
            repos.getRepository(WalletOperationRepository.class),repos.getRepository(WalletLedgerRepository.class),
            repos.getRepository(BookingDispatchEventRepository.class),mock(CustomerBookingService.class));
        job=new Job();job.setCustomerId(7L);job.setAssignedServiceProviderId(15L);job.setMarketId(1L);
        job.setServiceProviderTypeId(3L);job.setServiceOptionId(12L);job.setJobStatus("IN_PROGRESS");job.setPaymentStatus("PAID");
        job.setPaymentMethod("CARD");job.setSchedulingModel("ROUTE_BASED");job.setCurrencyCode("LKR");em.persist(job);
        request=new BookingRequest();request.setUserId(5L);request.setMarketId(1L);request.setOptionId(12L);request.setJobId(job.getJobId());
        request.setIdempotencyKey("one");request.setStatus("IN_PROGRESS");request.setPaymentStatus("PAID");
        request.setQuotedAmount(new BigDecimal("1000"));request.setCurrencyCode("LKR");request.setGatewayPaymentId("primary");
        request.setPaymentFundingMode("PAYHERE_SANDBOX");request.setStartCode("secret-code");request.setCheckoutJson("secret-checkout");
        request.setPayloadJson("private-booking-fields");request.setCandidatesJson("private-candidates");em.persist(request);
        other=new BookingRequest();other.setUserId(6L);other.setMarketId(2L);other.setOptionId(22L);other.setIdempotencyKey("two");
        other.setStatus("MATCHING");other.setPaymentStatus("NOT_STARTED");em.persist(other);
        for(String status:List.of("DECLINED","ACCEPTED")) {
            var o=new BookingOffer();o.setRequestId(request.getRequestId());o.setProviderId("DECLINED".equals(status)?16L:15L);
            o.setStatus(status);o.setExpiresAt(Instant.now().minusSeconds(30));em.persist(o);offer=o;
        }
        var a=new JobProviderAssignment();a.setJobId(job.getJobId());a.setServiceProviderId(17L);a.setStatus("COMPLETED");em.persist(a);
        for(String payment:List.of("primary","duplicate")) {
            var n=new BookingPaymentNotice();n.setRequestId(request.getRequestId());n.setPaymentId(payment);n.setStatusCode("2");
            n.setReceivedAt(Instant.parse("2026-10-25T08:00:00Z"));em.persist(n);
        }
        var failed=new BookingPaymentNotice();failed.setRequestId(other.getRequestId());failed.setPaymentId("failed");failed.setStatusCode("-2");
        failed.setReceivedAt(Instant.parse("2026-10-24T08:00:00Z"));em.persist(failed);
        operation=new WalletOperation();operation.setOperationKey("BOOKING:test:SETTLE");operation.setJobId(job.getJobId());
        operation.setType("CARD_BOOKING_SETTLE");operation.setOwnerUserId(5L);operation.setStatus("POSTED");
        operation.setFundingMode("PAYHERE_SANDBOX");operation.setPaymentReference("primary");operation.setAmount(new BigDecimal("1000"));em.persist(operation);
        for(var amount:List.of(new BigDecimal("-1000"),new BigDecimal("850"),new BigDecimal("150"))) {
            var e=new WalletLedgerEntry();e.setOperationId(operation.getOperationId());e.setAccountKey("test");e.setBucket("AVAILABLE");
            e.setCurrencyCode("LKR");e.setDelta(amount);em.persist(e);
        }
        var event=new BookingDispatchEvent();event.setRequestId(request.getRequestId());event.setUserId(15L);
        event.setEventType("BOOKING_OFFER");event.setDeliveryStatus("DELIVERED");event.setCreatedAt(Instant.now());em.persist(event);
        em.flush();em.clear();
    }
    @AfterEach void rollback(){em.getTransaction().rollback();em.close();}
    @Test void requestFiltersIncludeHistoricalOffersAndReadDoesNotAdvanceWorkflow() {
        var filter=new RequestFilter(null,null,5L,1L,12L,16L,"in_progress","paid",null,"payhere_sandbox",null,null);
        assertThat(service.requests(filter,0,20).getContent()).extracting(RequestRow::requestId).containsExactly(request.getRequestId());
        assertThat(service.requests(new RequestFilter(null,null,null,2L,null,16L,null,null,null,null,null,null),0,20)).isEmpty();
        assertThat(service.requests(new RequestFilter(null,null,null,null,null,null,null,null,null,null,null,null),0,1).getTotalElements()).isEqualTo(2);
        var result=service.request(other.getRequestId());assertThat(result.booking()).isNull();assertThat(result.request().status()).isEqualTo("MATCHING");
        assertThat(em.find(BookingOffer.class,offer.getOfferId()).getStatus()).isEqualTo("ACCEPTED");
    }
    @Test void bookingFiltersSupportPrimaryAndTeamProviderAndJoinRequestOnce() {
        for(Long provider:List.of(15L,17L)) {
            var result=service.bookings(new BookingFilter(null,7L,provider,1L,3L,12L,"IN_PROGRESS","PAID","CARD","ROUTE_BASED",null,null),0,20);
            assertThat(result.getContent()).hasSize(1);assertThat(result.getContent().get(0).requestId()).isEqualTo(request.getRequestId());
        }
        assertThat(service.booking(job.getJobId()).request().requestId()).isEqualTo(request.getRequestId());
        assertThatThrownBy(() -> service.booking(99999L)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void receiptFiltersDistinguishDuplicateChargesAndSandbox() {
        var rows=service.payments(new PaymentFilter(request.getRequestId(),null,"2","PAYHERE_SANDBOX",
            Instant.parse("2026-10-25T00:00:00Z"),Instant.parse("2026-10-26T00:00:00Z")),0,20).getContent();
        assertThat(rows).hasSize(2);assertThat(rows).filteredOn(PaymentRow::primaryPayment).hasSize(1);
        assertThat(rows).allSatisfy(p -> {assertThat(p.status()).isEqualTo("SUCCESS");assertThat(p.bookingAmount()).isEqualByComparingTo("1000");});
        assertThat(service.payments(new PaymentFilter(null,null,null,"PAYHERE",null,null),0,20)).isEmpty();
        assertThat(service.payments(new PaymentFilter(null,"failed","-2",null,null,null),0,20).getContent().get(0).status()).isEqualTo("FAILED");
    }
    @Test void transactionLedgerEventAndSummaryQueriesWorkAgainstStoredData() {
        assertThat(service.transactions(new TransactionFilter(job.getJobId(),5L,"CARD_BOOKING_SETTLE","POSTED","PAYHERE_SANDBOX","primary",null,null),0,20)
            .getContent()).extracting(TransactionRow::operationId).containsExactly(operation.getOperationId());
        assertThat(service.entries(operation.getOperationId(),0,20).stream().map(LedgerRow::delta).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("0");
        assertThatThrownBy(() -> service.entries(99999L,0,20)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.events(new EventFilter(request.getRequestId(),15L,"BOOKING_OFFER","DELIVERED",null,null),0,20)).hasSize(1);
        assertThat(service.offers(new OfferFilter(request.getRequestId(),null,null,null,null),0,1).getTotalElements()).isEqualTo(2);
        assertThat(service.summary().requestsByStatus()).containsEntry("MATCHING",1L).containsEntry("IN_PROGRESS",1L);
        assertThat(service.summary().requestSettlementsByStatus()).containsEntry("UNRECORDED",2L);
    }
    @Test void secretFieldsAreNotSerializedAndInvalidRangesAreRejected() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();
        assertThat(mapper.writeValueAsString(service.request(request.getRequestId())))
            .doesNotContain("secret-code","secret-checkout","private-booking-fields","private-candidates","startCode","checkoutJson","idempotencyKey");
        var now=LocalDateTime.now();
        assertThatThrownBy(() -> service.offers(new OfferFilter(null,null,null,now,now.minusDays(1)),0,20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.payments(new PaymentFilter(null,null,"99",null,null,null),0,20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.requests(new RequestFilter(null,null,null,null,null,null,null,null,null,null,null,null),0,101)).isInstanceOf(IllegalArgumentException.class);
    }
}
