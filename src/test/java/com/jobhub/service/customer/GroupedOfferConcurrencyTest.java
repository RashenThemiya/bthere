package com.jobhub.service.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.entity.job.*;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.ConflictException;
import com.jobhub.repository.job.*;
import jakarta.persistence.EntityManager;
import org.hibernate.cfg.Configuration;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GroupedOfferConcurrencyTest {
    @Test void twoConcurrentProvidersCreateExactlyOneReservationAndRevokeTheLoser() throws Exception {
        var config=new Configuration().setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
            .setProperty("hibernate.connection.driver_class","org.h2.Driver")
            .setProperty("hibernate.connection.url","jdbc:h2:mem:"+UUID.randomUUID()+";LOCK_TIMEOUT=10000")
            .setProperty("hibernate.hbm2ddl.auto","create-drop");
        for(var type:List.of(BookingRequest.class,BookingOffer.class,BookingDispatchEvent.class,Job.class,JobProviderAssignment.class))config.addAnnotatedClass(type);
        try(var factory=config.buildSessionFactory()) {
            Long requestId;var offerIds=new ArrayList<Long>();
            try(var em=factory.createEntityManager()) {
                em.getTransaction().begin();var r=new BookingRequest();r.setUserId(5L);r.setOptionId(12L);r.setMarketId(1L);
                r.setIdempotencyKey("concurrent-group");r.setStatus("MATCHING");r.setSelectionMode("OPEN_REQUEST");
                r.setExpiresAt(Instant.now().plusSeconds(600));r.setQuotedAmount(new BigDecimal("1000"));r.setCurrencyCode("LKR");
                r.setPayloadJson("{\"marketId\":1,\"booking\":{\"serviceTypeId\":3,\"optionId\":12,\"deliveryMode\":\"ONLINE\",\"paymentMethod\":\"CARD\"}}");
                em.persist(r);requestId=r.getRequestId();
                for(long pid:List.of(15L,16L)) {
                    var o=new BookingOffer();o.setRequestId(requestId);o.setProviderId(pid);o.setStatus("PENDING");
                    o.setExpiresAt(Instant.now().plusSeconds(60));em.persist(o);offerIds.add(o.getOfferId());
                }
                em.getTransaction().commit();
            }
            var count=new AtomicInteger();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
            try {
                var futures=new ArrayList<Future<String>>();
                for(int i=0;i<2;i++) {
                    final int index=i;
                    futures.add(pool.submit(() -> {
                        gate.await();try(var em=factory.createEntityManager()) {
                            em.getTransaction().begin();
                            try {
                                var view=service(em,count).accept(9L+index,offerIds.get(index));
                                em.getTransaction().commit();return view.status();
                            } catch(ConflictException e) {em.getTransaction().rollback();return e.getMessage();}
                        }
                    }));
                }
                gate.countDown();var outcomes=new ArrayList<String>();
                for(var f:futures)outcomes.add(f.get(15,TimeUnit.SECONDS));
                assertThat(outcomes).containsExactlyInAnyOrder("AWAITING_CUSTOMER_CONFIRMATION","Already accepted by another provider");
            } finally {pool.shutdownNow();}
            assertThat(count.get()).isEqualTo(1);
            try(var em=factory.createEntityManager()) {
                assertThat(em.createQuery("select count(j) from Job j",Long.class).getSingleResult()).isEqualTo(1);
                assertThat(em.createQuery("select o.status from BookingOffer o",String.class).getResultList()).containsExactlyInAnyOrder("ACCEPTED","CANCELLED");
                assertThat(em.createQuery("select count(e) from BookingDispatchEvent e where e.eventType='OFFER_CANCELLED'",Long.class).getSingleResult()).isEqualTo(1);
                assertThat(em.find(BookingRequest.class,requestId).getJobId()).isNotNull();
            }
        }
    }
    private BookingRequestService service(EntityManager em,AtomicInteger count) {
        var repos=new JpaRepositoryFactory(em);
        var providers=mock(com.jobhub.repository.provider.ServiceProviderRepository.class);
        for(long pid:List.of(15L,16L)) {
            var p=new ServiceProvider();p.setServiceProviderId(pid);p.setUserId(pid-6);
            when(providers.findById(pid)).thenReturn(Optional.of(p));when(providers.findByUserId(pid-6)).thenReturn(Optional.of(p));
        }
        var policies=mock(BookingRequestPolicyRepository.class);var p=new BookingRequestPolicy();p.setEnabled(true);
        when(policies.findById(12L)).thenReturn(Optional.of(p));
        var bookings=mock(CustomerBookingService.class);
        when(bookings.reserveRequestBooking(eq(5L),any())).thenAnswer(inv -> {
            com.jobhub.dto.job.CreateBookingRequest selected=inv.getArgument(1);
            count.incrementAndGet();var j=new Job();j.setMarketId(1L);j.setExpectedAmount(new BigDecimal("1000"));j.setCurrencyCode("LKR");j.setAssignedServiceProviderId(selected.providerId());
            em.persist(j);return j;
        });
        return new BookingRequestService(repos.getRepository(BookingRequestRepository.class),repos.getRepository(BookingOfferRepository.class),policies,
            repos.getRepository(BookingDispatchEventRepository.class),mock(BookingPaymentNoticeRepository.class),mock(com.jobhub.repository.provider.ServiceOptionRepository.class),
            providers,mock(com.jobhub.repository.market.MarketRepository.class),mock(com.jobhub.repository.auth.UserRepository.class),repos.getRepository(JobRepository.class),
            mock(CustomerDiscoveryService.class),bookings,mock(com.jobhub.payments.PayHereBookingCheckout.class),mock(BookingCodeCipher.class),
            new ObjectMapper().findAndRegisterModules(),mock(com.jobhub.service.audit.AuditService.class),mock(com.jobhub.service.finance.WalletService.class),
            mock(com.jobhub.payments.PaymentGateway.class),mock(com.jobhub.repository.finance.PaymentInstrumentRepository.class));
    }
}
