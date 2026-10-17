package com.jobhub.service.finance;

import com.jobhub.dto.customer.WalletApi.*;
import com.jobhub.entity.finance.*;
import com.jobhub.entity.job.Job;
import com.jobhub.exception.*;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.finance.*;
import com.jobhub.repository.job.*;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.jobhub.payments.PaymentGateway selectedGateway;
    private String paymentProvider="DISABLED";
    private static final BigDecimal LIMIT = new BigDecimal("99999999999999.9999");
    private final WalletRepository wallets;
    private final WalletOperationRepository operations;
    private final WalletLedgerRepository ledger;
    private final UserRepository users;
    private final JobRepository jobs;
    private final CustomerRepository customers;
    private final JobProviderAssignmentRepository jobProviders;
    private final ServiceProviderRepository providers;
    private final MarketRepository markets;
    private final AuditService audit;

    public List<Balance> list(Long userId) {
        return wallets.findAllByUserIdOrderByCurrencyCodeAsc(userId).stream()
                .filter(w -> "LKR".equals(w.getCurrencyCode())).map(this::balance).toList();
    }
    public Balance get(Long userId, String currency) { return balance(owned(userId, currency(currency))); }

    @Transactional
    public Balance open(Long userId, String currency) {
        String selectedCurrency = currency(currency);
        if (markets.findAllByStatusOrderByNameAsc("ACTIVE").stream().noneMatch(m -> currencyEquals(m.getDefaultCurrency(), selectedCurrency))) {
            throw new IllegalArgumentException("Currency is not enabled in an active market");
        }
        lockUsers(List.of(userId));
        return balance(lockedWallet(userId, selectedCurrency));
    }
    public Page<Entry> history(Long userId, String currency, Pageable pageable) {
        var wallet = owned(userId, currency(currency));
        return ledger.findAllByWalletIdOrderByWalletLedgerEntryIdDesc(wallet.getWalletId(), pageable)
                .map(e -> new Entry(e.getWalletLedgerEntryId(), e.getOperationId(), e.getCurrencyCode(), e.getBucket(),
                        e.getDelta(), e.getBalanceBefore(), e.getBalanceAfter(), e.getCreatedAt()));
    }
    @Transactional
    public Reconciliation reconcile(Long userId, String currency) {
        lockUsers(List.of(userId));
        var wallet = wallets.lockOwned(userId, currency(currency)).orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        var totals = totals(wallet);
        boolean balanced = matches(wallet.getAvailableBalance(), totals.get("AVAILABLE"))
                && matches(wallet.getReservedBalance(), totals.get("RESERVED")) && matches(wallet.getPendingBalance(), totals.get("PENDING"));
        return new Reconciliation(wallet.getWalletId(), balanced, totals.get("AVAILABLE"), totals.get("RESERVED"), totals.get("PENDING"));
    }

    @Transactional
    public Operation reserveBooking(Long userId, Long jobId) {
        var job = job(jobId);
        var customer = customers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
        if (!customer.getCustomerId().equals(job.getCustomerId())) throw new ResourceNotFoundException("Booking not found");
        if (!"WALLET".equals(job.getPaymentMethod())) throw new IllegalArgumentException("This booking does not use WALLET");
        String key = "BOOKING:" + jobId + ":RESERVE";
        var previous = operations.findByOperationKey(key);
        if (previous.isPresent()) return response(previous.get());
        if (!Set.of("PENDING", "ACCEPTED", "CONFIRMED").contains(job.getJobStatus()) || !"PENDING".equals(job.getPaymentStatus())) {
            throw new ConflictException("Booking cannot be reserved in its current state");
        }
        String currency = currency(job.getCurrencyCode());
        BigDecimal amount = money(job.getExpectedAmount(), currency);
        lockUsers(List.of(userId));
        var wallet = lockedWallet(userId, currency);
        var operation = operation(key, "BOOKING_RESERVE", userId, job, amount, userId, "POSTED");
        post(operation, List.of(new Posting(wallet, "AVAILABLE", amount.negate()), new Posting(wallet, "RESERVED", amount)));
        job.setPaymentStatus("RESERVED"); jobs.save(job);
        return response(operation);
    }

    /** Internal SPI integration entry point; never accepts raw HTTP payment claims. */
    @Transactional
    public void gatewayMovement(Long userId, String currency, BigDecimal amount, Long intentId, String effect) {
        if (!"MOCK".equalsIgnoreCase(selectedGateway == null ? paymentProvider : selectedGateway.name())) throw new PaymentUnavailableException("Mock ledger effects are disabled");
        currency = currency(currency); amount = money(amount,currency);
        lockUsers(List.of(userId));
        String key="GATEWAY:"+intentId+":"+effect;
        if (operations.findByOperationKey(key).isPresent()) return;
        var wallet=lockedWallet(userId,currency);
        if (!"MOCK".equals(wallet.getFundingMode()) && (bucket(wallet,"AVAILABLE").signum()!=0
                || bucket(wallet,"RESERVED").signum()!=0 || bucket(wallet,"PENDING").signum()!=0)) {
            throw new ConflictException("Mock and existing non-mock funds cannot be mixed");
        }
        wallet.setFundingMode("MOCK");
        var op=new WalletOperation();op.setOperationKey(key);op.setType(effect);op.setOwnerUserId(userId);
        op.setCurrencyCode(currency);op.setAmount(amount);op.setActorUserId(userId);op.setStatus("POSTED");op=operations.save(op);
        List<Posting> postings=switch(effect) {
            case "TOPUP" -> List.of(new Posting(null,"EXTERNAL_CLEARING",amount.negate()),new Posting(wallet,"AVAILABLE",amount));
            case "TOPUP_REFUND" -> List.of(new Posting(wallet,"AVAILABLE",amount.negate()),new Posting(null,"EXTERNAL_CLEARING",amount));
            case "WITHDRAW_RESERVE" -> List.of(new Posting(wallet,"AVAILABLE",amount.negate()),new Posting(wallet,"RESERVED",amount));
            case "WITHDRAW_SUCCESS" -> List.of(new Posting(wallet,"RESERVED",amount.negate()),new Posting(null,"EXTERNAL_CLEARING",amount));
            case "WITHDRAW_RELEASE" -> List.of(new Posting(wallet,"RESERVED",amount.negate()),new Posting(wallet,"AVAILABLE",amount));
            default -> throw new IllegalArgumentException("Unknown gateway ledger effect");
        };
        post(op,postings);
    }

    @Transactional
    public Operation releaseCancelledBooking(Long actorId, Long jobId) {
        var job = job(jobId);
        if (!Set.of("CANCELLED", "REJECTED", "EXPIRED").contains(job.getJobStatus())) {
            throw new ConflictException("Only cancelled, rejected or expired bookings can release reserved funds");
        }
        var reservation = reservation(jobId);
        requireReservationCurrency(job, reservation);
        String key = "BOOKING:" + jobId + ":RELEASE";
        var previous = operations.findByOperationKey(key);
        if (previous.isPresent()) return response(previous.get());
        if (!"RESERVED".equals(job.getPaymentStatus()) || operations.findByOperationKey("BOOKING:" + jobId + ":SETTLE")
                .filter(o -> "POSTED".equals(o.getStatus())).isPresent()) throw new ConflictException("Funds are not releasable");
        lockUsers(List.of(reservation.getOwnerUserId()));
        var wallet = lockedWallet(reservation.getOwnerUserId(), reservation.getCurrencyCode());
        var operation = operation(key, "BOOKING_RELEASE", reservation.getOwnerUserId(), job, reservation.getAmount(), actorId, "POSTED");
        post(operation, List.of(new Posting(wallet, "RESERVED", reservation.getAmount().negate()),
                new Posting(wallet, "AVAILABLE", reservation.getAmount())));
        job.setPaymentStatus("RELEASED"); jobs.save(job);
        return response(operation);
    }

    @Transactional
    public Operation requestSettlement(Long actorId, Long jobId) {
        var job = job(jobId);
        String key = "BOOKING:" + jobId + ":SETTLE";
        var previous = operations.findByOperationKey(key);
        if (previous.isPresent()) return response(previous.get());
        if (!"COMPLETED".equals(job.getJobStatus()) || !"RESERVED".equals(job.getPaymentStatus())) {
            throw new ConflictException("Settlement requires a completed wallet-reserved booking");
        }
        var reserved = reservation(jobId);
        requireReservationCurrency(job, reserved);
        var op = operation(key, "BOOKING_SETTLE", reserved.getOwnerUserId(), job, reserved.getAmount(), actorId, "PENDING_APPROVAL");
        audit.record(actorId, "WALLET_SETTLEMENT_REQUESTED", "WALLET_OPERATION", op.getOperationId(), null, response(op));
        return response(op);
    }

    @Transactional
    public Operation approveSettlement(Long actorId, Long jobId) {
        var job = job(jobId);
        var op = operations.findByOperationKey("BOOKING:" + jobId + ":SETTLE")
                .orElseThrow(() -> new ResourceNotFoundException("Settlement request not found"));
        requireReservationCurrency(job, reservation(jobId));
        if (!op.getCurrencyCode().equals(job.getCurrencyCode())) throw new ConflictException("Settlement currency changed");
        if (actorId.equals(op.getActorUserId())) throw new ConflictException("A different finance administrator must approve settlement");
        if ("POSTED".equals(op.getStatus())) return response(op);
        if (!"PENDING_APPROVAL".equals(op.getStatus()) || !"COMPLETED".equals(job.getJobStatus()) || !"RESERVED".equals(job.getPaymentStatus())) {
            throw new ConflictException("Settlement state changed");
        }
        if (money(job.getTotalAmount(), op.getCurrencyCode()).compareTo(op.getAmount()) != 0) {
            throw new ConflictException("Booking amount changed; reconciliation is required");
        }
        List<Long> providerIds = jobProviders.findAllByJobIdOrderByJobProviderAssignmentIdAsc(jobId).stream()
                .filter(p -> "ASSIGNED".equals(p.getStatus()) || "COMPLETED".equals(p.getStatus()))
                .map(p -> p.getServiceProviderId()).distinct().sorted().toList();
        if (providerIds.isEmpty() && job.getAssignedServiceProviderId() != null) providerIds = List.of(job.getAssignedServiceProviderId());
        if (providerIds.isEmpty()) throw new ConflictException("No assigned provider found for settlement");
        List<Long> recipients = providerIds.stream().map(id -> providers.findById(id)
                .orElseThrow(() -> new ConflictException("Provider not found")).getUserId()).distinct().sorted().toList();
        if (recipients.contains(op.getOwnerUserId())) throw new ConflictException("Customer cannot settle earnings to themselves");
        List<Long> allUsers = new ArrayList<>(recipients); allUsers.add(op.getOwnerUserId()); lockUsers(allUsers);
        BigDecimal fee = job.getPlatformFee() == null ? BigDecimal.ZERO : nonnegative(job.getPlatformFee(), op.getCurrencyCode());
        if (fee.compareTo(op.getAmount()) >= 0) throw new ConflictException("Invalid platform fee");
        BigDecimal net = op.getAmount().subtract(fee);
        int scale = Currency.getInstance(op.getCurrencyCode()).getDefaultFractionDigits();
        BigDecimal share = net.divide(BigDecimal.valueOf(recipients.size()), scale, RoundingMode.DOWN);
        List<Posting> postings = new ArrayList<>();
        postings.add(new Posting(lockedWallet(op.getOwnerUserId(), op.getCurrencyCode()), "RESERVED", op.getAmount().negate()));
        for (int i = 0; i < recipients.size(); i++) {
            BigDecimal value = i == recipients.size()-1 ? net.subtract(share.multiply(BigDecimal.valueOf(i))) : share;
            if (value.signum() > 0) postings.add(new Posting(lockedWallet(recipients.get(i), op.getCurrencyCode()), "PENDING", value));
        }
        if (fee.signum() > 0) postings.add(new Posting(null, "PLATFORM_FEE", fee));
        post(op, postings);
        op.setStatus("POSTED"); operations.save(op); job.setPaymentStatus("SETTLED"); jobs.save(job);
        audit.record(actorId, "WALLET_SETTLEMENT_APPROVED", "WALLET_OPERATION", op.getOperationId(), null, response(op));
        return response(op);
    }

    private void post(WalletOperation op, List<Posting> postings) {
        currency(op.getCurrencyCode());
        if (postings.stream().map(Posting::delta).reduce(BigDecimal.ZERO, BigDecimal::add).signum() != 0) {
            throw new IllegalStateException("Unbalanced wallet operation");
        }
        var affected = postings.stream().map(Posting::wallet).filter(Objects::nonNull).distinct().toList();
        boolean mockPosting = affected.stream().anyMatch(w -> "MOCK".equals(w.getFundingMode()));
        for (var wallet : affected) {
            if (mockPosting && !"MOCK".equals(wallet.getFundingMode()) && (bucket(wallet,"AVAILABLE").signum()!=0
                    || bucket(wallet,"RESERVED").signum()!=0 || bucket(wallet,"PENDING").signum()!=0)) {
                throw new ConflictException("Mock and non-mock funds cannot be mixed in settlement");
            }
            if ("MOCK".equals(wallet.getFundingMode()) && !"MOCK".equalsIgnoreCase(selectedGateway == null ? paymentProvider : selectedGateway.name())) {
                throw new ConflictException("Mock funds cannot be used outside mock mode");
            }
            if (!op.getCurrencyCode().equals(wallet.getCurrencyCode())) throw new ConflictException("Cross-currency wallet posting is forbidden");
            if (!"ACTIVE".equals(wallet.getStatus())) throw new ConflictException("Wallet is not active");
            var totals = totals(wallet);
            if (!matches(wallet.getAvailableBalance(), totals.get("AVAILABLE")) || !matches(wallet.getReservedBalance(), totals.get("RESERVED"))
                    || !matches(wallet.getPendingBalance(), totals.get("PENDING"))) throw new ConflictException("Wallet reconciliation failed; funds are blocked");
        }
        // Preflight every bucket before changing a managed entity or persisting entries.
        for (var p : postings) {
            if (p.wallet() == null) continue;
            BigDecimal after = bucket(p.wallet(), p.bucket()).add(p.delta());
            if (after.signum() < 0) throw new ConflictException("Insufficient " + p.bucket().toLowerCase() + " balance");
            if (after.compareTo(LIMIT) > 0) throw new ConflictException("Wallet limit exceeded");
        }
        if (mockPosting) affected.forEach(w -> w.setFundingMode("MOCK"));
        for (var p : postings) {
            var entry = new WalletLedgerEntry(); entry.setOperationId(op.getOperationId());
            entry.setCurrencyCode(op.getCurrencyCode()); entry.setDelta(p.delta()); entry.setBucket(p.bucket());
            if (p.wallet() != null) {
                var wallet = p.wallet(); BigDecimal before = bucket(wallet, p.bucket()), after = before.add(p.delta());
                entry.setWalletId(wallet.getWalletId()); entry.setAccountKey("WALLET:" + wallet.getWalletId());
                entry.setBalanceBefore(before); entry.setBalanceAfter(after); setBucket(wallet, p.bucket(), after); wallets.save(wallet);
            } else entry.setAccountKey("SYSTEM:" + p.bucket() + ":" + op.getCurrencyCode());
            ledger.save(entry);
        }
        op.setStatus("POSTED"); operations.save(op);
        audit.record(op.getActorUserId(), "WALLET_POSTED", "WALLET_OPERATION", op.getOperationId(), null, response(op));
    }
    private Map<String, BigDecimal> totals(Wallet wallet) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (String bucket : List.of("AVAILABLE", "RESERVED", "PENDING")) totals.put(bucket, BigDecimal.ZERO);
        for (var entry : ledger.lockCurrentEntries(wallet.getWalletId())) {
            if (!wallet.getCurrencyCode().equals(entry.getCurrencyCode()) || !totals.containsKey(entry.getBucket())) {
                throw new ConflictException("Invalid wallet ledger currency or bucket");
            }
            totals.merge(entry.getBucket(), entry.getDelta(), BigDecimal::add);
        }
        return totals;
    }
    private WalletOperation reservation(Long jobId) {
        return operations.findByOperationKey("BOOKING:" + jobId + ":RESERVE").filter(o -> "POSTED".equals(o.getStatus()))
                .orElseThrow(() -> new ConflictException("Wallet reservation not found"));
    }
    private void requireReservationCurrency(Job job, WalletOperation reservation) {
        if (!reservation.getCurrencyCode().equals(job.getCurrencyCode())) throw new ConflictException("Reserved booking currency changed");
    }
    private WalletOperation operation(String key, String type, Long owner, Job job, BigDecimal amount, Long actor, String status) {
        var op = new WalletOperation(); op.setOperationKey(key); op.setType(type); op.setOwnerUserId(owner);
        op.setJobId(job.getJobId()); op.setCurrencyCode(currency(job.getCurrencyCode())); op.setAmount(amount);
        op.setActorUserId(actor); op.setStatus(status); return operations.save(op);
    }
    private Wallet lockedWallet(Long userId, String currency) {
        currency(currency);
        return wallets.lockOwned(userId, currency).orElseGet(() -> {
            var wallet = new Wallet(); wallet.setUserId(userId); wallet.setCurrencyCode(currency); wallet.setStatus("ACTIVE");
            wallet.setAvailableBalance(BigDecimal.ZERO); wallet.setPendingBalance(BigDecimal.ZERO); wallet.setReservedBalance(BigDecimal.ZERO);
            wallet.setFundingMode("UNFUNDED");
            return wallets.save(wallet);
        });
    }
    private Wallet owned(Long userId, String currency) {
        return wallets.findByUserIdAndCurrencyCode(userId, currency).orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    }
    private Job job(Long id) {
        var job = jobs.lockPaymentJob(id).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        currency(job.getCurrencyCode());
        return job;
    }
    private void lockUsers(List<Long> ids) {
        ids.stream().distinct().sorted().forEach(id -> users.lockById(id).filter(u -> "ACTIVE".equals(u.getStatus()))
                .orElseThrow(() -> new ConflictException("Wallet owner is not active")));
    }
    public static String currency(String value) {
        if (!"LKR".equals(value)) throw new IllegalArgumentException("Only LKR is supported for payments and wallets in this release");
        if (value == null || !value.matches("[A-Z]{3}")) throw new IllegalArgumentException("Use an uppercase ISO currency code");
        try { if (Currency.getInstance(value).getDefaultFractionDigits() < 0 || Currency.getInstance(value).getDefaultFractionDigits() > 4) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unsupported wallet currency"); }
        return value;
    }
    public static BigDecimal money(BigDecimal amount, String currency) {
        BigDecimal value = nonnegative(amount, currency);
        if (value.signum() == 0) throw new IllegalArgumentException("Amount must be positive");
        return value;
    }
    private static BigDecimal nonnegative(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() < 0 || amount.compareTo(LIMIT) > 0) throw new IllegalArgumentException("Invalid amount");
        try { return amount.setScale(Currency.getInstance(currency(currency)).getDefaultFractionDigits(), RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Amount has too many fractional digits"); }
    }
    private boolean currencyEquals(String actual, String expected) { return expected.equals(actual); }
    private boolean matches(BigDecimal value, BigDecimal expected) { return value != null && value.compareTo(expected) == 0; }
    private BigDecimal bucket(Wallet w, String bucket) {
        BigDecimal value = switch (bucket) { case "AVAILABLE" -> w.getAvailableBalance(); case "RESERVED" -> w.getReservedBalance(); case "PENDING" -> w.getPendingBalance(); default -> throw new IllegalStateException("Invalid bucket"); };
        if (value == null) throw new ConflictException("Legacy wallet needs ledger migration"); return value;
    }
    private void setBucket(Wallet w, String bucket, BigDecimal value) {
        switch (bucket) { case "AVAILABLE" -> w.setAvailableBalance(value); case "RESERVED" -> w.setReservedBalance(value); case "PENDING" -> w.setPendingBalance(value); default -> throw new IllegalStateException(); }
    }
    private Balance balance(Wallet w) { return new Balance(w.getWalletId(), w.getCurrencyCode(), w.getAvailableBalance(), w.getReservedBalance(), w.getPendingBalance(), w.getStatus(), w.getFundingMode()); }
    private Operation response(WalletOperation op) { return new Operation(op.getOperationId(), op.getType(), op.getJobId(), op.getCurrencyCode(), op.getAmount(), op.getStatus()); }
    private record Posting(Wallet wallet, String bucket, BigDecimal delta) {}
}

