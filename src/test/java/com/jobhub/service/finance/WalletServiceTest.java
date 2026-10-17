package com.jobhub.service.finance;

import com.jobhub.entity.auth.User;
import com.jobhub.entity.customer.Customer;
import com.jobhub.entity.finance.*;
import com.jobhub.entity.job.Job;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.*;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.finance.*;
import com.jobhub.repository.job.*;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.service.audit.AuditService;
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
class WalletServiceTest {
    @Mock WalletRepository wallets;
    @Mock WalletOperationRepository operations;
    @Mock WalletLedgerRepository ledger;
    @Mock UserRepository users;
    @Mock JobRepository jobs;
    @Mock CustomerRepository customers;
    @Mock JobProviderAssignmentRepository jobProviders;
    @Mock ServiceProviderRepository providers;
    @Mock MarketRepository markets;
    @Mock AuditService audit;
    @InjectMocks WalletService service;
    Map<Long, Wallet> balances = new HashMap<>();
    Map<String, WalletOperation> ops = new HashMap<>();
    List<WalletLedgerEntry> entries = new ArrayList<>();
    Job job;

    @BeforeEach void setup() {
        job = new Job(); job.setJobId(100L); job.setCustomerId(7L); job.setCurrencyCode("LKR");
        job.setExpectedAmount(new BigDecimal("20.00")); job.setTotalAmount(new BigDecimal("20.00"));
        job.setJobStatus("PENDING"); job.setPaymentMethod("WALLET"); job.setPaymentStatus("PENDING"); job.setAssignedServiceProviderId(15L);
        var customer = new Customer(); customer.setCustomerId(7L);
        lenient().when(customers.findByUserId(25L)).thenReturn(Optional.of(customer));
        lenient().when(jobs.lockPaymentJob(100L)).thenReturn(Optional.of(job));
        lenient().when(operations.findByOperationKey(anyString())).thenAnswer(i -> Optional.ofNullable(ops.get(i.getArgument(0))));
        lenient().when(operations.save(any())).thenAnswer(i -> {
            WalletOperation op = i.getArgument(0); if (op.getOperationId() == null) op.setOperationId((long)ops.size()+1);
            ops.put(op.getOperationKey(), op); return op;
        });
        lenient().when(users.lockById(anyLong())).thenAnswer(i -> { var user = new User(); user.setUserId(i.getArgument(0)); user.setStatus("ACTIVE"); return Optional.of(user); });
        lenient().when(wallets.lockOwned(anyLong(), eq("LKR"))).thenAnswer(i -> Optional.ofNullable(balances.get(i.getArgument(0))));
        lenient().when(wallets.save(any())).thenAnswer(i -> { Wallet w = i.getArgument(0); if(w.getWalletId()==null)w.setWalletId(w.getUserId()); balances.put(w.getUserId(),w); return w; });
        lenient().when(ledger.lockCurrentEntries(anyLong())).thenAnswer(i -> entries.stream().filter(e -> i.getArgument(0).equals(e.getWalletId())).toList());
        lenient().when(ledger.save(any())).thenAnswer(i -> { WalletLedgerEntry e=i.getArgument(0);entries.add(e);return e; });
        var provider = new ServiceProvider(); provider.setServiceProviderId(15L); provider.setUserId(42L);
        lenient().when(providers.findById(15L)).thenReturn(Optional.of(provider));
        seedTestFixture(25L, "100.00");
    }
    @Test void releaseOneRejectsOtherCurrenciesBeforeAccessingWallets() {
        org.springframework.test.util.ReflectionTestUtils.setField(service,"paymentProvider","MOCK");
        assertThat(WalletService.currency("LKR")).isEqualTo("LKR");
        for (String currency : List.of("USD", "EUR", "lkr")) {
            assertThatThrownBy(() -> service.open(25L,currency)).hasMessageContaining("Only LKR");
            assertThatThrownBy(() -> service.get(25L,currency)).hasMessageContaining("Only LKR");
            assertThatThrownBy(() -> service.history(25L,currency,org.springframework.data.domain.PageRequest.of(0,20))).hasMessageContaining("Only LKR");
            assertThatThrownBy(() -> service.gatewayMovement(25L,currency,new BigDecimal("1"),1L,"TOPUP"))
                .hasMessageContaining("Only LKR");
        }
        job.setCurrencyCode("USD");
        assertThatThrownBy(() -> service.reserveBooking(25L,100L)).hasMessageContaining("Only LKR");
        verify(wallets,never()).save(any());
    }
    @Test void listDoesNotExposeUnsupportedLegacyWallets() {
        var usd=new Wallet(); usd.setCurrencyCode("USD");
        when(wallets.findAllByUserIdOrderByCurrencyCodeAsc(25L)).thenReturn(List.of(balances.get(25L),usd));
        assertThat(service.list(25L)).extracting(com.jobhub.dto.customer.WalletApi.Balance::currencyCode).containsExactly("LKR");
    }
    // Test-only opening balance; there is deliberately no HTTP funding/credit bypass.
    void seedTestFixture(Long userId, String amount) {
        var w = new Wallet(); w.setWalletId(userId);w.setUserId(userId);w.setCurrencyCode("LKR");w.setStatus("ACTIVE");
        w.setAvailableBalance(new BigDecimal(amount));w.setReservedBalance(BigDecimal.ZERO);w.setPendingBalance(BigDecimal.ZERO);balances.put(userId,w);
        var e = new WalletLedgerEntry(); e.setWalletId(userId);e.setCurrencyCode("LKR");e.setBucket("AVAILABLE");e.setDelta(new BigDecimal(amount));entries.add(e);
    }
    @Test void reservesWithBalancedLedgerEntriesAndDuplicateCallsDoNotSpendAgain() {
        var first=service.reserveBooking(25L,100L);var repeated=service.reserveBooking(25L,100L);
        assertThat(repeated.operationId()).isEqualTo(first.operationId());
        assertThat(balances.get(25L).getAvailableBalance()).isEqualByComparingTo("80");
        assertThat(balances.get(25L).getReservedBalance()).isEqualByComparingTo("20");
        assertThat(entries.stream().filter(e -> first.operationId().equals(e.getOperationId())).map(WalletLedgerEntry::getDelta)
                .reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("0");
        assertThat(entries).hasSize(3);assertThat(job.getPaymentStatus()).isEqualTo("RESERVED");
    }
    @Test void insufficientFundsNeverCreateLedgerPostings() {
        entries.clear();seedTestFixture(25L,"1");
        assertThatThrownBy(() -> service.reserveBooking(25L,100L)).isInstanceOf(ConflictException.class).hasMessageContaining("Insufficient");
        verify(ledger,never()).save(any());assertThat(balances.get(25L).getAvailableBalance()).isEqualByComparingTo("1");
    }
    @Test void tamperedOrLegacyUnreconciledBalanceCannotBeSpent() {
        balances.get(25L).setAvailableBalance(new BigDecimal("999"));
        assertThatThrownBy(() -> service.reserveBooking(25L,100L)).hasMessageContaining("reconciliation");
        verify(ledger,never()).save(any());
    }
    @Test void anotherCustomerCannotReserveThisBooking() {
        var other = new Customer();other.setCustomerId(8L);when(customers.findByUserId(26L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.reserveBooking(26L,100L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ledger);
    }
    @Test void directCardBookingCannotDebitWallet() {
        job.setPaymentMethod("CARD");
        assertThatThrownBy(() -> service.reserveBooking(25L,100L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ledger);
    }
    @Test void precisionAndCurrencyMustBeExplicitAndExact() {
        assertThatThrownBy(() -> WalletService.money(new BigDecimal("1.001"),"LKR")).hasMessageContaining("fractional");
        assertThatThrownBy(() -> WalletService.money(BigDecimal.ZERO,"LKR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WalletService.money(new BigDecimal("-1"),"LKR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WalletService.currency("lkr")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WalletService.money(new BigDecimal("1"),"JPY")).hasMessageContaining("Only LKR");
        assertThatThrownBy(() -> WalletService.money(new BigDecimal("1.1"),"JPY")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void onlyTerminalCancellationCanReleaseAndRetriesDoNotRefundTwice() {
        service.reserveBooking(25L,100L);
        assertThatThrownBy(() -> service.releaseCancelledBooking(99L,100L)).isInstanceOf(ConflictException.class);
        job.setJobStatus("CANCELLED");service.releaseCancelledBooking(99L,100L);service.releaseCancelledBooking(99L,100L);
        assertThat(balances.get(25L).getAvailableBalance()).isEqualByComparingTo("100");
        assertThat(balances.get(25L).getReservedBalance()).isEqualByComparingTo("0");
        assertThat(entries).hasSize(5);
    }
    @Test void completedJobNeedsTwoDifferentFinanceActorsAndCreditsOnlyPendingEarnings() {
        service.reserveBooking(25L,100L);job.setJobStatus("COMPLETED");job.setPlatformFee(new BigDecimal("2"));
        assertThat(service.requestSettlement(98L,100L).status()).isEqualTo("PENDING_APPROVAL");
        assertThatThrownBy(() -> service.approveSettlement(98L,100L)).hasMessageContaining("different finance");
        service.approveSettlement(99L,100L);int count=entries.size();service.approveSettlement(99L,100L);
        assertThat(entries).hasSize(count);assertThat(job.getPaymentStatus()).isEqualTo("SETTLED");
        assertThat(balances.get(42L).getAvailableBalance()).isEqualByComparingTo("0");
        assertThat(balances.get(42L).getPendingBalance()).isEqualByComparingTo("18");
        assertThat(balances.get(25L).getReservedBalance()).isEqualByComparingTo("0");
        var settlement=ops.get("BOOKING:100:SETTLE");
        assertThat(entries.stream().filter(e -> settlement.getOperationId().equals(e.getOperationId())).map(WalletLedgerEntry::getDelta)
                .reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("0");
    }
    @Test void changedAmountOrCurrencyBlocksSettlement() {
        service.reserveBooking(25L,100L);job.setJobStatus("COMPLETED");service.requestSettlement(98L,100L);
        job.setTotalAmount(new BigDecimal("21"));
        assertThatThrownBy(() -> service.approveSettlement(99L,100L)).hasMessageContaining("amount changed");
        job.setCurrencyCode("USD");
        assertThatThrownBy(() -> service.approveSettlement(99L,100L)).hasMessageContaining("Only LKR");
    }
    @Test void frozenWalletsAreBlocked() {
        balances.get(25L).setStatus("FROZEN");
        assertThatThrownBy(() -> service.reserveBooking(25L,100L)).hasMessageContaining("not active");
    }
    @Test void selfSettlementIsBlocked() {
        service.reserveBooking(25L,100L);job.setJobStatus("COMPLETED");
        var p=new ServiceProvider();p.setUserId(25L);when(providers.findById(15L)).thenReturn(Optional.of(p));service.requestSettlement(98L,100L);
        assertThatThrownBy(() -> service.approveSettlement(99L,100L)).hasMessageContaining("themselves");
    }
    @Test void failedReservationUsesTransactionRollback() {
        balances.get(25L).setStatus("FROZEN");
        var manager=mock(org.springframework.transaction.PlatformTransactionManager.class);
        var tx=mock(org.springframework.transaction.TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(tx);
        var proxy=new org.springframework.aop.framework.ProxyFactory(service);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional=(WalletService)proxy.getProxy();
        assertThatThrownBy(() -> transactional.reserveBooking(25L,100L)).isInstanceOf(ConflictException.class);
        verify(manager).rollback(tx);verify(manager,never()).commit(any());
    }
    @Test void mockFundingAndPayoutUseBalancedLedgerAndCannotBeSpentAfterSwitchingOff() {
        entries.clear();balances.clear();seedTestFixture(25L,"0");
        org.springframework.test.util.ReflectionTestUtils.setField(service,"paymentProvider","MOCK");
        service.gatewayMovement(25L,"LKR",new BigDecimal("100"),1L,"TOPUP");
        service.gatewayMovement(25L,"LKR",new BigDecimal("100"),1L,"TOPUP");
        assertThat(balances.get(25L).getAvailableBalance()).isEqualByComparingTo("100");
        service.gatewayMovement(25L,"LKR",new BigDecimal("30"),2L,"WITHDRAW_RESERVE");
        service.gatewayMovement(25L,"LKR",new BigDecimal("30"),2L,"WITHDRAW_SUCCESS");
        assertThat(balances.get(25L).getAvailableBalance()).isEqualByComparingTo("70");
        assertThat(balances.get(25L).getReservedBalance()).isEqualByComparingTo("0");
        org.springframework.test.util.ReflectionTestUtils.setField(service,"paymentProvider","DISABLED");
        assertThatThrownBy(() -> service.reserveBooking(25L,100L)).hasMessageContaining("outside mock mode");
    }
    @Test void mockFundsCannotMixWithExistingBalances() {
        org.springframework.test.util.ReflectionTestUtils.setField(service,"paymentProvider","MOCK");
        assertThatThrownBy(() -> service.gatewayMovement(25L,"LKR",BigDecimal.TEN,1L,"TOPUP")).hasMessageContaining("cannot be mixed");
    }
}


