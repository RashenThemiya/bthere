package com.jobhub.service.finance;

import com.jobhub.entity.finance.*;
import com.jobhub.entity.job.*;
import com.jobhub.entity.auth.User;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.entity.market.Market;
import com.jobhub.repository.finance.*;
import com.jobhub.repository.job.*;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.provider.*;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.service.audit.AuditService;
import com.jobhub.service.customer.*;
import com.jobhub.payments.PayHereBookingCheckout;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WalletRequestPersistenceTest {
    @Test void concurrentConfirmationAndCompletionPostOnceAndFailuresRollBackWholeLifecycle() throws Exception {
        var config=new Configuration().setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
            .setProperty("hibernate.connection.driver_class","org.h2.Driver")
            .setProperty("hibernate.connection.url","jdbc:h2:mem:"+UUID.randomUUID()+";LOCK_TIMEOUT=10000")
            .setProperty("hibernate.hbm2ddl.auto","create-drop");
        for(var type:List.of(BookingRequest.class,BookingOffer.class,BookingDispatchEvent.class,BookingPaymentNotice.class,
                Job.class,JobProviderAssignment.class,Wallet.class,WalletOperation.class,WalletLedgerEntry.class))config.addAnnotatedClass(type);
        try(var factory=config.buildSessionFactory()) {
            Long jobId,requestId;
            try(var em=factory.createEntityManager()) {
                em.getTransaction().begin();
                var j=new Job();j.setMarketId(1L);j.setCustomerId(7L);j.setAssignedServiceProviderId(15L);j.setCurrencyCode("LKR");
                j.setExpectedAmount(new BigDecimal("20"));j.setTotalAmount(new BigDecimal("20"));j.setPlatformFeePercentage(new BigDecimal("10"));
                j.setPlatformFee(new BigDecimal("2"));j.setProviderEarnings(new BigDecimal("18"));j.setJobStatus("AWAITING_PAYMENT");
                j.setPaymentMethod("WALLET");j.setPaymentStatus("PENDING");j.setStartDatetime(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
                j.setExpectedEndDatetime(j.getStartDatetime().plusHours(1));em.persist(j);jobId=j.getJobId();
                var r=new BookingRequest();r.setUserId(25L);r.setJobId(jobId);r.setIdempotencyKey("wallet-test");r.setStatus("AWAITING_CUSTOMER_CONFIRMATION");
                r.setConfirmationExpiresAt(Instant.now().plusSeconds(120));r.setPayloadJson("{\"marketId\":1,\"booking\":{\"paymentMethod\":\"WALLET\"}}");
                r.setPaymentStatus("NOT_STARTED");r.setCurrencyCode("LKR");r.setQuotedAmount(j.getTotalAmount());
                r.setPlatformFeePercentage(j.getPlatformFeePercentage());r.setPlatformFee(j.getPlatformFee());r.setProviderEarnings(j.getProviderEarnings());
                em.persist(r);requestId=r.getRequestId();
                var wallet=new Wallet();wallet.setUserId(25L);wallet.setCurrencyCode("LKR");wallet.setStatus("ACTIVE");wallet.setFundingMode("PAYHERE");
                wallet.setAvailableBalance(new BigDecimal("100"));wallet.setReservedBalance(BigDecimal.ZERO);wallet.setPendingBalance(BigDecimal.ZERO);em.persist(wallet);
                var seed=new WalletOperation();seed.setOperationKey("test:funding");seed.setCurrencyCode("LKR");seed.setAmount(new BigDecimal("100"));
                seed.setStatus("POSTED");seed.setType("TEST_SEED");seed.setFundingMode("PAYHERE");em.persist(seed);
                for(boolean system:List.of(false,true)) {
                    var e=new WalletLedgerEntry();e.setOperationId(seed.getOperationId());e.setCurrencyCode("LKR");e.setBucket("AVAILABLE");
                    e.setAccountKey(system?"SYSTEM:TEST":"WALLET:"+wallet.getWalletId());e.setWalletId(system?null:wallet.getWalletId());
                    e.setDelta(new BigDecimal(system?"-100":"100"));em.persist(e);
                }
                em.getTransaction().commit();
            }
            assertThatThrownBy(() -> tx(factory,em -> service(em,mock(AuditService.class),false).confirmWallet(25L,requestId)))
                .hasMessageContaining("32-byte");
            assertBalances(factory,"100","0",0);
            try(var em=factory.createEntityManager()) {
                assertThat(em.find(BookingRequest.class,requestId).getStatus()).isEqualTo("AWAITING_CUSTOMER_CONFIRMATION");
                assertThat(em.createQuery("select count(o) from WalletOperation o where o.type='BOOKING_RESERVE'",Long.class).getSingleResult()).isZero();
            }
            concurrent(() -> tx(factory,em -> service(em,mock(AuditService.class),true).confirmWallet(25L,requestId).status()));
            assertBalances(factory,"80","20",0);
            tx(factory,em -> {
                var s=service(em,mock(AuditService.class),true);
                var code=s.customerCodes(25L,jobId).code();s.verifyStartCode(42L,jobId,code);return s.requestCompletion(42L,jobId);
            });
            var failing=mock(AuditService.class);
            doThrow(new IllegalStateException("audit failure")).when(failing).record(any(),anyString(),anyString(),any(),any(),any());
            assertThatThrownBy(() -> tx(factory,em -> service(em,failing,true).confirmCompletion(25L,jobId))).hasMessageContaining("audit failure");
            assertBalances(factory,"80","20",0);
            try(var em=factory.createEntityManager()) {assertThat(em.find(Job.class,jobId).getJobStatus()).isEqualTo("AWAITING_COMPLETION_CONFIRMATION");}
            concurrent(() -> tx(factory,em -> service(em,mock(AuditService.class),true).confirmCompletion(25L,jobId).status()));
            assertBalances(factory,"80","0",18);
            try(var em=factory.createEntityManager()) {
                assertThat(em.createQuery("select count(o) from WalletOperation o where o.type='BOOKING_RESERVE'",Long.class).getSingleResult()).isEqualTo(1);
                assertThat(em.createQuery("select count(o) from WalletOperation o where o.type='WALLET_BOOKING_SETTLE'",Long.class).getSingleResult()).isEqualTo(1);
                assertThat(em.createQuery("select sum(e.delta) from WalletLedgerEntry e",BigDecimal.class).getSingleResult()).isEqualByComparingTo("0");
                assertThat(em.find(BookingRequest.class,requestId).getPaymentStatus()).isEqualTo("SETTLED");
            }
        }
    }
    private void assertBalances(SessionFactory factory,String available,String reserved,int earnings) {
        try(var em=factory.createEntityManager()) {
            var payer=em.createQuery("select w from Wallet w where w.userId=25",Wallet.class).getSingleResult();
            assertThat(payer.getAvailableBalance()).isEqualByComparingTo(available);assertThat(payer.getReservedBalance()).isEqualByComparingTo(reserved);
            var providers=em.createQuery("select w from Wallet w where w.userId=42",Wallet.class).getResultList();
            if(earnings==0)assertThat(providers).isEmpty();else assertThat(providers.get(0).getAvailableBalance()).isEqualByComparingTo(Integer.toString(earnings));
        }
    }
    private void concurrent(Callable<String> call) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {Callable<String> task=() -> {gate.await();return call.call();};var a=pool.submit(task);var b=pool.submit(task);gate.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
    }
    private <T> T tx(SessionFactory factory,Function<EntityManager,T> action) {
        try(var em=factory.createEntityManager()) {
            em.getTransaction().begin();try {var result=action.apply(em);em.getTransaction().commit();return result;}
            catch(RuntimeException e){em.getTransaction().rollback();throw e;}
        }
    }
    private BookingRequestService service(EntityManager em,AuditService audit,boolean validKey) {
        var repos=new JpaRepositoryFactory(em);var requests=repos.getRepository(BookingRequestRepository.class);
        var users=mock(UserRepository.class);var user=new User();user.setStatus("ACTIVE");when(users.lockById(anyLong())).thenReturn(Optional.of(user));
        var customers=mock(CustomerRepository.class);var customer=new Customer();customer.setCustomerId(7L);when(customers.findByUserId(25L)).thenReturn(Optional.of(customer));
        var providers=mock(ServiceProviderRepository.class);var provider=new ServiceProvider();provider.setServiceProviderId(15L);provider.setUserId(42L);
        when(providers.findById(15L)).thenReturn(Optional.of(provider));when(providers.findByUserId(42L)).thenReturn(Optional.of(provider));
        var marketRepo=mock(MarketRepository.class);var market=new Market();market.setTimezone("UTC");when(marketRepo.findById(1L)).thenReturn(Optional.of(market));
        var jobs=repos.getRepository(JobRepository.class);var notices=repos.getRepository(BookingPaymentNoticeRepository.class);
        var walletService=new WalletService(repos.getRepository(WalletRepository.class),repos.getRepository(WalletOperationRepository.class),
            repos.getRepository(WalletLedgerRepository.class),users,jobs,customers,mock(JobProviderAssignmentRepository.class),providers,marketRepo,audit,requests,notices);
        return new BookingRequestService(requests,repos.getRepository(BookingOfferRepository.class),mock(BookingRequestPolicyRepository.class),
            repos.getRepository(BookingDispatchEventRepository.class),notices,mock(ServiceOptionRepository.class),providers,marketRepo,users,jobs,
            mock(CustomerDiscoveryService.class),mock(CustomerBookingService.class),mock(PayHereBookingCheckout.class),
            new BookingCodeCipher(validKey?Base64.getEncoder().encodeToString(new byte[32]):"invalid"),new ObjectMapper().findAndRegisterModules(),audit,walletService);
    }
}
