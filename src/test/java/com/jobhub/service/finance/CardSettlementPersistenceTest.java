package com.jobhub.service.finance;

import com.jobhub.entity.finance.*;
import com.jobhub.entity.job.*;
import com.jobhub.entity.auth.User;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.repository.finance.*;
import com.jobhub.repository.job.*;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.service.audit.AuditService;
import org.hibernate.cfg.Configuration;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CardSettlementPersistenceTest {
    @Test void concurrentSettlementCreditsOnceAndFailedCompletionRollsBackAllWrites() throws Exception {
        var config=new Configuration().setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
            .setProperty("hibernate.connection.driver_class","org.h2.Driver")
            .setProperty("hibernate.connection.url","jdbc:h2:mem:"+UUID.randomUUID()+";LOCK_TIMEOUT=10000")
            .setProperty("hibernate.hbm2ddl.auto","create-drop");
        for(var type:List.of(BookingRequest.class,BookingOffer.class,JobProviderAssignment.class,Job.class,Wallet.class,WalletOperation.class,
                WalletLedgerEntry.class,BookingPaymentNotice.class))config.addAnnotatedClass(type);
        try(var factory=config.buildSessionFactory()) {
            Long jobId;
            try(var em=factory.createEntityManager()) {
                em.getTransaction().begin();
                var j=new Job();j.setCustomerId(7L);j.setCurrencyCode("LKR");j.setExpectedAmount(new BigDecimal("20"));
                j.setTotalAmount(new BigDecimal("20"));j.setPlatformFeePercentage(new BigDecimal("10"));j.setPlatformFee(new BigDecimal("2"));
                j.setProviderEarnings(new BigDecimal("18"));j.setJobStatus("AWAITING_COMPLETION_CONFIRMATION");j.setPaymentStatus("PAID");
                j.setPaymentMethod("CARD");j.setAssignedServiceProviderId(15L);em.persist(j);jobId=j.getJobId();
                var r=new BookingRequest();r.setUserId(25L);r.setJobId(jobId);r.setIdempotencyKey("test");
                r.setStatus(j.getJobStatus());r.setPaymentStatus("PAID");r.setGatewayPaymentId("receipt");r.setPaymentFundingMode("PAYHERE");
                r.setCurrencyCode("LKR");r.setQuotedAmount(j.getTotalAmount());r.setPlatformFeePercentage(j.getPlatformFeePercentage());
                r.setPlatformFee(j.getPlatformFee());r.setProviderEarnings(j.getProviderEarnings());em.persist(r);
                var n=new BookingPaymentNotice();n.setRequestId(r.getRequestId());n.setPaymentId("receipt");n.setStatusCode("2");em.persist(n);
                em.getTransaction().commit();
            }
            var audit=mock(AuditService.class);
            doThrow(new IllegalStateException("audit failure")).when(audit).record(any(),anyString(),anyString(),any(),any(),any());
            assertThatThrownBy(() -> complete(factory,jobId,audit)).hasMessageContaining("audit failure");
            try(var em=factory.createEntityManager()) {
                assertThat(em.find(Job.class,jobId).getJobStatus()).isEqualTo("AWAITING_COMPLETION_CONFIRMATION");
                assertThat(em.createQuery("select count(w) from Wallet w",Long.class).getSingleResult()).isZero();
                assertThat(em.createQuery("select count(o) from WalletOperation o",Long.class).getSingleResult()).isZero();
                assertThat(em.createQuery("select count(e) from WalletLedgerEntry e",Long.class).getSingleResult()).isZero();
            }
            var pool=Executors.newFixedThreadPool(2);
            try {
                var gate=new CountDownLatch(1);
                Callable<Long> task=() -> {gate.await();return complete(factory,jobId,mock(AuditService.class));};
                var a=pool.submit(task);var b=pool.submit(task);gate.countDown();
                assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
            } finally {pool.shutdownNow();}
            try(var em=factory.createEntityManager()) {
                assertThat(em.createQuery("select w.availableBalance from Wallet w",BigDecimal.class).getSingleResult()).isEqualByComparingTo("18");
                assertThat(em.createQuery("select count(o) from WalletOperation o",Long.class).getSingleResult()).isEqualTo(1);
                assertThat(em.createQuery("select sum(e.delta) from WalletLedgerEntry e",BigDecimal.class).getSingleResult()).isEqualByComparingTo("0");
                assertThat(em.find(Job.class,jobId).getPaymentStatus()).isEqualTo("SETTLED");
            }
        }
    }
    private Long complete(org.hibernate.SessionFactory factory,Long jobId,AuditService audit) {
        try(var em=factory.createEntityManager()) {
            em.getTransaction().begin();
            try {
                var repos=new JpaRepositoryFactory(em);
                var requests=repos.getRepository(BookingRequestRepository.class);
                var r=requests.lockByJobId(jobId).orElseThrow();r.setStatus("COMPLETED");
                em.find(Job.class,jobId).setJobStatus("COMPLETED");
                var users=mock(UserRepository.class);var user=new User();user.setStatus("ACTIVE");
                when(users.lockById(anyLong())).thenReturn(Optional.of(user));
                var customers=mock(CustomerRepository.class);var customer=new Customer();customer.setCustomerId(7L);
                when(customers.findByUserId(25L)).thenReturn(Optional.of(customer));
                var providers=mock(ServiceProviderRepository.class);var provider=new ServiceProvider();provider.setUserId(42L);
                when(providers.findById(15L)).thenReturn(Optional.of(provider));
                var service=new WalletService(repos.getRepository(WalletRepository.class),repos.getRepository(WalletOperationRepository.class),
                    repos.getRepository(WalletLedgerRepository.class),users,repos.getRepository(JobRepository.class),customers,
                    mock(JobProviderAssignmentRepository.class),providers,mock(MarketRepository.class),audit,requests,
                    repos.getRepository(BookingPaymentNoticeRepository.class));
                var result=service.settleCardBooking(25L,jobId);em.getTransaction().commit();return result.operationId();
            } catch(RuntimeException ex) {em.getTransaction().rollback();throw ex;}
        }
    }
}

