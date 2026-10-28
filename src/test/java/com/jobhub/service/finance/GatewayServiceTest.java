package com.jobhub.service.finance;
import com.jobhub.payments.MockPaymentGateway;
import com.jobhub.entity.finance.*;
import com.jobhub.entity.auth.User;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.customer.Customer;
import com.jobhub.repository.finance.*;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class GatewayServiceTest {
    @Mock GatewayIntentRepository intents;
    @Mock PaymentInstrumentRepository methods;
    @Mock UserRepository users;
    @Mock CustomerRepository customers;
    @Mock JobRepository jobs;
    @Mock WalletService wallets;
    GatewayService service;
    Map<Long,GatewayIntent> state=new HashMap<>();
    @BeforeEach void setup() {
        service=new GatewayService(new MockPaymentGateway(),intents,methods,users,customers,jobs,wallets);
        var user=new User();user.setStatus("ACTIVE");lenient().when(users.lockById(25L)).thenReturn(Optional.of(user));
        lenient().when(intents.findByUserIdAndProviderAndRequestKey(eq(25L),eq("MOCK"),anyString()))
                .thenAnswer(i -> state.values().stream().filter(v -> i.getArgument(2).equals(v.getRequestKey())).findFirst());
        lenient().when(intents.save(any())).thenAnswer(i -> {GatewayIntent v=i.getArgument(0);if(v.getIntentId()==null)v.setIntentId((long)state.size()+1);state.put(v.getIntentId(),v);return v;});
        lenient().when(intents.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(state.get(i.getArgument(0))));
        lenient().when(intents.lockOwned(anyLong(),anyLong())).thenAnswer(i -> Optional.ofNullable(state.get(i.getArgument(0))).filter(v -> i.getArgument(1).equals(v.getUserId())));
    }
    @Test void topupCreditsOnlyOnSuccessAndReplayDoesNotCreditAgain() {
        var first=service.startWallet(25L,"LKR",new BigDecimal("100"),null,"TOPUP","key1");
        assertThat(first.status()).isEqualTo("PENDING");
        verify(wallets,never()).gatewayMovement(anyLong(),anyString(),any(),anyLong(),anyString());
        service.simulate(25L,first.intentId(),"PENDING");service.simulate(25L,first.intentId(),"SUCCESS");service.simulate(25L,first.intentId(),"SUCCESS");
        verify(wallets,times(1)).gatewayMovement(25L,"LKR",new BigDecimal("100.00"),first.intentId(),"TOPUP");
        assertThat(service.startWallet(25L,"LKR",new BigDecimal("100"),null,"TOPUP","key1").intentId()).isEqualTo(first.intentId());
    }
    @Test void changedIdempotentRequestAndTerminalOutcomeAreRejected() {
        var first=service.startWallet(25L,"LKR",BigDecimal.TEN,null,"TOPUP","key1");
        assertThatThrownBy(() -> service.startWallet(25L,"LKR",BigDecimal.ONE,null,"TOPUP","key1")).isInstanceOf(ConflictException.class);
        service.simulate(25L,first.intentId(),"FAILURE");
        assertThatThrownBy(() -> service.simulate(25L,first.intentId(),"SUCCESS")).isInstanceOf(ConflictException.class);
        verify(wallets,never()).gatewayMovement(anyLong(),anyString(),any(),anyLong(),anyString());
    }
    @Test void cannotReadOrSimulateAnotherUsersIntent() {
        var first=service.startWallet(25L,"LKR",BigDecimal.TEN,null,"TOPUP","key1");
        assertThatThrownBy(() -> service.get(26L,first.intentId())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.simulate(26L,first.intentId(),"SUCCESS")).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void withdrawalReservesAndFailedPayoutReleasesExactlyOnce() {
        var method=new PaymentInstrument();method.setUserId(25L);method.setProvider("MOCK");method.setType("BANK_ACCOUNT");method.setStatus("VERIFIED_MOCK");
        when(methods.findById(5L)).thenReturn(Optional.of(method));
        var first=service.startWallet(25L,"LKR",BigDecimal.TEN,5L,"WITHDRAWAL","withdraw1");
        verify(wallets).gatewayMovement(25L,"LKR",new BigDecimal("10.00"),first.intentId(),"WITHDRAW_RESERVE");
        service.simulate(25L,first.intentId(),"FAILURE");service.simulate(25L,first.intentId(),"FAILURE");
        verify(wallets,times(1)).gatewayMovement(25L,"LKR",new BigDecimal("10.00"),first.intentId(),"WITHDRAW_RELEASE");
    }
    @Test void withdrawalNeedsOwnedVerifiedBankMethod() {
        assertThatThrownBy(() -> service.startWallet(25L,"LKR",BigDecimal.TEN,null,"WITHDRAWAL","k")).isInstanceOf(IllegalArgumentException.class);
        var other=new PaymentInstrument();other.setUserId(26L);when(methods.findById(5L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.startWallet(25L,"LKR",BigDecimal.TEN,5L,"WITHDRAWAL","k")).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void successfulTopupCanBeRefundedOnlyOnce() {
        var first=service.startWallet(25L,"LKR",BigDecimal.TEN,null,"TOPUP","k");
        service.simulate(25L,first.intentId(),"SUCCESS");service.simulate(25L,first.intentId(),"REFUND");service.simulate(25L,first.intentId(),"REFUND");
        verify(wallets,times(1)).gatewayMovement(25L,"LKR",new BigDecimal("10.00"),first.intentId(),"TOPUP_REFUND");
    }
    @Test void directCardNeverDebitsWalletAndHasOneIntentPerBooking() {
        var job=new Job();job.setJobId(100L);job.setCustomerId(7L);job.setCurrencyCode("LKR");job.setExpectedAmount(BigDecimal.TEN);
        job.setPaymentMethod("CARD");job.setJobStatus("PENDING");job.setPaymentStatus("PENDING");
        var customer=new Customer();customer.setCustomerId(7L);
        when(jobs.lockPaymentJob(100L)).thenReturn(Optional.of(job));when(customers.findByUserId(25L)).thenReturn(Optional.of(customer));
        var first=service.startCard(25L,100L,"a");assertThat(service.startCard(25L,100L,"b").intentId()).isEqualTo(first.intentId());
        service.simulate(25L,first.intentId(),"SUCCESS");assertThat(job.getPaymentStatus()).isEqualTo("PAID");
        verifyNoInteractions(wallets);
    }
    @Test void clientKeysCannotCollideWithInternalCardBookingKeys() {
        var topup=service.startWallet(25L,"LKR",BigDecimal.TEN,null,"TOPUP","CARD_JOB:100");
        var job=new Job();job.setJobId(100L);job.setCustomerId(7L);job.setCurrencyCode("LKR");job.setExpectedAmount(BigDecimal.TEN);
        job.setPaymentMethod("CARD");job.setJobStatus("PENDING");job.setPaymentStatus("PENDING");
        var customer=new Customer();customer.setCustomerId(7L);
        when(jobs.lockPaymentJob(100L)).thenReturn(Optional.of(job));when(customers.findByUserId(25L)).thenReturn(Optional.of(customer));
        var card=service.startCard(25L,100L,"retry1");
        assertThat(card.intentId()).isNotEqualTo(topup.intentId());assertThat(card.purpose()).isEqualTo("CARD");
    }
    @Test void mockCardReturnsBrandIncludingLegacyRows() {
        when(methods.findByUserIdAndProviderAndType(25L,"MOCK","CARD")).thenReturn(Optional.empty());
        when(methods.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.setup(25L,"CARD").brand()).isEqualTo("VISA");
        var legacy=new PaymentInstrument();legacy.setType("CARD");legacy.setStatus("VERIFIED_MOCK");
        var bank=new PaymentInstrument();bank.setType("BANK_ACCOUNT");bank.setStatus("VERIFIED_MOCK");
        when(methods.findAllByUserIdAndProvider(25L,"MOCK")).thenReturn(List.of(legacy,bank));
        assertThat(service.methods(25L)).extracting(m -> m.brand()).containsExactly("VISA",null);
    }
    @Test void removeDeletesOnlyOwnedMethodWithoutPendingPayment() {
        var own=new PaymentInstrument();own.setMethodId(5L);own.setUserId(25L);own.setProvider("MOCK");own.setType("CARD");
        var other=new PaymentInstrument();other.setMethodId(6L);other.setUserId(26L);other.setProvider("MOCK");
        when(methods.findById(5L)).thenReturn(Optional.of(own));when(methods.findById(6L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.remove(25L,6L)).isInstanceOf(ResourceNotFoundException.class);
        when(intents.existsByMethodIdAndStatus(5L,"PENDING")).thenReturn(true);
        assertThatThrownBy(() -> service.remove(25L,5L)).isInstanceOf(ConflictException.class);
        verify(methods,never()).delete(any());
        when(intents.existsByMethodIdAndStatus(5L,"PENDING")).thenReturn(false);
        service.remove(25L,5L);
        verify(methods).delete(own);
    }
}
