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
    private final BookingRequestRepository bookingRequests;
    private final BookingPaymentNoticeRepository paymentNotices;

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
        var page=ledger.findAllByWalletIdOrderByWalletLedgerEntryIdDesc(wallet.getWalletId(),pageable);
        var metadata=new HashMap<Long,WalletOperation>();
        operations.findAllById(page.getContent().stream().map(WalletLedgerEntry::getOperationId).distinct().toList())
                .forEach(op -> metadata.put(op.getOperationId(),op));
        return page.map(e -> {
            var op=metadata.get(e.getOperationId());
            return new Entry(e.getWalletLedgerEntryId(),e.getOperationId(),e.getCurrencyCode(),e.getBucket(),e.getDelta(),
                    e.getBalanceBefore(),e.getBalanceAfter(),e.getCreatedAt(),op==null?null:op.getType(),op==null?null:op.getJobId(),
                    op==null?null:op.getAmount(),op==null?null:op.getPlatformFeePercentage(),op==null?null:op.getPlatformFee(),
                    op==null?null:op.getProviderEarnings(),op==null?null:op.getFundingMode(),op==null?null:op.getPaymentReference());
        });
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
        requireLegacyBooking(jobId);
        return reserve(userId,jobId,false);
    }
    /** Internal request confirmation hook; its caller owns the surrounding atomic confirmation. */
    @Transactional
    public Operation reserveRequestBooking(Long userId,Long jobId) {
        var r=bookingRequests.lockByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking request not found"));
        if(!userId.equals(r.getUserId()))throw new ResourceNotFoundException("Booking not found");
        if(!"AWAITING_CUSTOMER_CONFIRMATION".equals(r.getStatus()) || r.getConfirmationExpiresAt()==null
                || !java.time.Instant.now().isBefore(r.getConfirmationExpiresAt()))
            throw new ConflictException("Wallet reservation requires timely customer confirmation after provider acceptance");
        return reserve(userId,jobId,true);
    }
    private Operation reserve(Long userId,Long jobId,boolean requestFlow) {
        var job = job(jobId);
        var customer = customers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
        if (!customer.getCustomerId().equals(job.getCustomerId())) throw new ResourceNotFoundException("Booking not found");
        if (!"WALLET".equals(job.getPaymentMethod())) throw new IllegalArgumentException("This booking does not use WALLET");
        String key = "BOOKING:" + jobId + ":RESERVE";
        var previous = operations.findLockedByOperationKey(key);
        if (previous.isPresent()) {
            var op=previous.get();
            if(requestFlow && (!"BOOKING_RESERVE".equals(op.getType()) || !"POSTED".equals(op.getStatus())
                    || !userId.equals(op.getOwnerUserId()) || !"RESERVED".equals(job.getPaymentStatus())
                    || !Objects.equals(op.getCurrencyCode(),job.getCurrencyCode())
                    || money(job.getExpectedAmount(),job.getCurrencyCode()).compareTo(op.getAmount())!=0))
                throw new ConflictException("Existing wallet reservation does not match the booking");
            return response(op);
        }
        if (!(requestFlow ? Set.of("AWAITING_PAYMENT") : Set.of("PENDING", "ACCEPTED", "CONFIRMED")).contains(job.getJobStatus()) || !"PENDING".equals(job.getPaymentStatus())) {
            throw new ConflictException("Booking cannot be reserved in its current state");
        }
        String currency = currency(job.getCurrencyCode());
        BigDecimal amount = money(job.getExpectedAmount(), currency);
        lockUsers(List.of(userId));
        var wallet = lockedWallet(userId, currency);
        if("PAYHERE_SANDBOX".equals(wallet.getFundingMode()))throw new ConflictException("Sandbox earnings cannot fund wallet bookings");
        if(requestFlow && bucket(wallet,"AVAILABLE").compareTo(amount)<0)
            throw new InsufficientWalletFundsException(currency,amount,bucket(wallet,"AVAILABLE"));
        if(requestFlow && !Set.of("PAYHERE","MOCK").contains(wallet.getFundingMode()==null?"":wallet.getFundingMode()))
            throw new ConflictException("Wallet funding source requires reconciliation before booking");
        var operation = operation(key, "BOOKING_RESERVE", userId, job, amount, userId, "POSTED");
        if("PAYHERE".equals(wallet.getFundingMode()) || requestFlow)operation.setFundingMode(wallet.getFundingMode());
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
        op.setFundingMode("MOCK");
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
        requireLegacyBooking(jobId);
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
        requireLegacyBooking(jobId);
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
        requireLegacyBooking(jobId);
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
        if (fee.compareTo(op.getAmount()) > 0) throw new ConflictException("Invalid platform fee");
        BigDecimal net = op.getAmount().subtract(fee);
        op.setPlatformFeePercentage(job.getPlatformFeePercentage());op.setPlatformFee(fee);op.setProviderEarnings(net);
        int scale = Currency.getInstance(op.getCurrencyCode()).getDefaultFractionDigits();
        BigDecimal share = net.divide(BigDecimal.valueOf(recipients.size()), scale, RoundingMode.DOWN);
        List<Posting> postings = new ArrayList<>();
        var payer=lockedWallet(op.getOwnerUserId(),op.getCurrencyCode());
        if("PAYHERE".equals(payer.getFundingMode()))op.setFundingMode("PAYHERE");
        postings.add(new Posting(payer, "RESERVED", op.getAmount().negate()));
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

    /** Internal completion hook. Payment proof is persisted by the verified PayHere callback only. */
    @Transactional
    public Operation settleCardBooking(Long customerUserId,Long jobId) {return settleRequestBooking(customerUserId,jobId,false);}
    @Transactional
    public Operation settleRequestWalletBooking(Long customerUserId,Long jobId) {return settleRequestBooking(customerUserId,jobId,true);}
    private Operation settleRequestBooking(Long customerUserId,Long jobId,boolean walletPayment) {
        var request=bookingRequests.lockByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking request not found"));
        if(!customerUserId.equals(request.getUserId()))throw new ResourceNotFoundException("Booking not found");
        var job=job(jobId);
        var customer=customers.findByUserId(customerUserId).orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if(!customer.getCustomerId().equals(job.getCustomerId()))throw new ResourceNotFoundException("Booking not found");
        String expectedMethod=walletPayment?"WALLET":"CARD";
        if(!expectedMethod.equals(job.getPaymentMethod()) || !"COMPLETED".equals(job.getJobStatus())
                || !"COMPLETED".equals(request.getStatus())
                || !(walletPayment?Set.of("RESERVED","SETTLED"):Set.of("PAID")).contains(request.getPaymentStatus()))
            throw new ConflictException("Settlement requires customer-confirmed completion and verified payment");
        WalletOperation reserved=null;
        String reference;
        String mode=request.getPaymentFundingMode();
        if(walletPayment) {
            reserved=reservation(jobId);requireReservationCurrency(job,reserved);
            if(!customerUserId.equals(reserved.getOwnerUserId()) || !"WALLET".equals(job.getPaymentMethod())
                    || !Objects.equals(mode,reserved.getFundingMode()) || !Set.of("PAYHERE","MOCK").contains(mode==null?"":mode))
                throw new ConflictException("Wallet reservation funding proof does not match the booking");
            reference="WALLET_RESERVATION:"+reserved.getOperationId();
        } else {
            reference=request.getGatewayPaymentId();
            if(reference==null || paymentNotices.findByPaymentIdAndStatusCode(reference,"2")
                    .filter(n -> request.getRequestId().equals(n.getRequestId())).isEmpty()
                    || paymentNotices.findByPaymentIdAndStatusCode(reference,"-3").isPresent())
                throw new ConflictException("Verified successful card payment is required for settlement");
            if(!Set.of("PAYHERE","PAYHERE_SANDBOX").contains(mode==null?"":mode))
                throw new ConflictException("Payment environment is missing; merchant reconciliation is required");
        }
        String currency=currency(job.getCurrencyCode());
        BigDecimal gross=money(request.getQuotedAmount(),currency);
        if(gross.compareTo(money(job.getExpectedAmount(),currency))!=0 || gross.compareTo(money(job.getTotalAmount(),currency))!=0
                || !currency.equals(request.getCurrencyCode()) || job.getPlatformFeePercentage()==null
                || request.getPlatformFeePercentage()==null || job.getPlatformFee()==null || job.getProviderEarnings()==null)
            throw new ConflictException("Saved booking amounts or commission snapshot changed");
        var split=com.jobhub.service.market.PlatformFeeService.allocate(gross,job.getPlatformFeePercentage(),"SNAPSHOT");
        if(split.percentage().compareTo(request.getPlatformFeePercentage())!=0
                || split.platformFee().compareTo(nonnegative(job.getPlatformFee(),currency))!=0
                || split.platformFee().compareTo(nonnegative(request.getPlatformFee(),currency))!=0
                || split.providerEarnings().compareTo(nonnegative(job.getProviderEarnings(),currency))!=0
                || split.providerEarnings().compareTo(nonnegative(request.getProviderEarnings(),currency))!=0)
            throw new ConflictException("Saved booking fee split does not reconcile");
        if(walletPayment && gross.compareTo(reserved.getAmount())!=0)throw new ConflictException("Wallet reserved amount changed");
        String settlementType=walletPayment?"WALLET_BOOKING_SETTLE":"CARD_BOOKING_SETTLE";
        String key="BOOKING:"+jobId+":SETTLE";
        var previous=operations.findLockedByOperationKey(key);
        if(previous.isPresent()) {
            var op=previous.get();
            if(!settlementType.equals(op.getType()) || !"POSTED".equals(op.getStatus())
                    || !"SETTLED".equals(job.getPaymentStatus()) || !reference.equals(op.getPaymentReference())
                    || !mode.equals(op.getFundingMode()) || !currency.equals(op.getCurrencyCode())
                    || gross.compareTo(op.getAmount())!=0 || split.platformFee().compareTo(op.getPlatformFee())!=0
                    || split.providerEarnings().compareTo(op.getProviderEarnings())!=0)
                throw new ConflictException("An incompatible settlement already exists");
            request.setSettlementStatus("POSTED");request.setSettlementOperationId(op.getOperationId());
            if(walletPayment)request.setPaymentStatus("SETTLED");
            return response(op);
        }
        if(!(walletPayment?"RESERVED":"PAID").equals(job.getPaymentStatus()))throw new ConflictException("Booking is not available for settlement");
        var assigned=jobProviders.findAllByJobIdOrderByJobProviderAssignmentIdAsc(jobId).stream()
                .filter(a -> Set.of("ASSIGNED","COMPLETED").contains(a.getStatus()))
                .map(a -> a.getServiceProviderId()).distinct().toList();
        if(job.getAssignedServiceProviderId()==null || assigned.size()>1
                || assigned.size()==1 && !assigned.get(0).equals(job.getAssignedServiceProviderId()))
            throw new ConflictException("Automatic request settlement supports one assigned provider");
        var provider=providers.findById(job.getAssignedServiceProviderId()).orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
        if(customerUserId.equals(provider.getUserId()))throw new ConflictException("Customer cannot settle earnings to themselves");
        lockUsers(List.of(customerUserId,provider.getUserId()));
        var wallet=lockedWallet(provider.getUserId(),currency);
        Wallet payer=walletPayment?lockedWallet(customerUserId,currency):null;
        if(walletPayment && !mode.equals(payer.getFundingMode()))throw new ConflictException("Wallet funding source changed");
        var op=operation(key,settlementType,customerUserId,job,gross,customerUserId,"PROCESSING");
        op.setFundingMode(mode);op.setPaymentReference(reference);op.setPlatformFeePercentage(split.percentage());
        op.setPlatformFee(split.platformFee());op.setProviderEarnings(split.providerEarnings());
        var postings=new ArrayList<Posting>();
        postings.add(walletPayment?new Posting(payer,"RESERVED",gross.negate()):
            new Posting(null,"PAYHERE_SANDBOX".equals(mode)?"PAYHERE_SANDBOX_CLEARING":"PAYHERE_CLEARING",gross.negate()));
        postings.add(new Posting(wallet,"AVAILABLE",split.providerEarnings()));
        if(split.platformFee().signum()>0)postings.add(new Posting(null,
                "PAYHERE_SANDBOX".equals(mode)?"SANDBOX_PLATFORM_FEE":"MOCK".equals(mode)?"MOCK_PLATFORM_FEE":"PLATFORM_FEE",split.platformFee()));
        post(op,postings);
        job.setPaymentStatus("SETTLED");jobs.save(job);
        request.setSettlementStatus("POSTED");request.setSettlementOperationId(op.getOperationId());
        if(walletPayment)request.setPaymentStatus("SETTLED");
        audit.record(customerUserId,walletPayment?"WALLET_REQUEST_BOOKING_SETTLED":"CARD_BOOKING_SETTLED","WALLET_OPERATION",op.getOperationId(),null,response(op));
        return response(op);
    }

    /** Chargeback does not silently reverse already-earned/spent money; freeze it for finance review. */
    @Transactional
    public void freezeCardSettlement(Long customerUserId,Long jobId) {
        var request=bookingRequests.lockByJobId(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking request not found"));
        if(!customerUserId.equals(request.getUserId()) || !"CHARGEDBACK".equals(request.getPaymentStatus())
                || paymentNotices.findByPaymentIdAndStatusCode(request.getGatewayPaymentId(),"-3")
                        .filter(n -> request.getRequestId().equals(n.getRequestId())).isEmpty())
            throw new ConflictException("Verified chargeback is required");
        var op=operations.findLockedByOperationKey("BOOKING:"+jobId+":SETTLE");
        if(op.isEmpty() || !"CARD_BOOKING_SETTLE".equals(op.get().getType()) || !"POSTED".equals(op.get().getStatus())
                || op.get().getProviderEarnings().signum()==0)return;
        var job=job(jobId);var provider=providers.findById(job.getAssignedServiceProviderId()).orElseThrow();
        lockUsers(List.of(provider.getUserId()));
        var wallet=wallets.lockOwned(provider.getUserId(),currency(job.getCurrencyCode())).orElseThrow();
        if(!"FROZEN".equals(wallet.getStatus())) {
            String old=wallet.getStatus();wallet.setStatus("FROZEN");wallets.save(wallet);
            audit.record(null,"CARD_SETTLEMENT_CHARGEBACK_REVIEW","WALLET",wallet.getWalletId(),
                    Map.of("status",old),Map.of("status","FROZEN","jobId",jobId,"operationId",op.get().getOperationId()));
        }
    }

    private void post(WalletOperation op, List<Posting> postings) {
        currency(op.getCurrencyCode());
        if (postings.stream().map(Posting::delta).reduce(BigDecimal.ZERO, BigDecimal::add).signum() != 0) {
            throw new IllegalStateException("Unbalanced wallet operation");
        }
        var affected = postings.stream().map(Posting::wallet).filter(Objects::nonNull).distinct().toList();
        String explicitFunding=op.getFundingMode();
        boolean cardFunds="PAYHERE".equals(explicitFunding) || "PAYHERE_SANDBOX".equals(explicitFunding);
        boolean mockPosting = !cardFunds && affected.stream().anyMatch(w -> "MOCK".equals(w.getFundingMode()));
        for (var wallet : affected) {
            if(cardFunds && !explicitFunding.equals(wallet.getFundingMode()) && hasFunds(wallet))
                throw new ConflictException("Sandbox, live card and other wallet funds cannot be mixed");
            if (mockPosting && !"MOCK".equals(wallet.getFundingMode()) && (bucket(wallet,"AVAILABLE").signum()!=0
                    || bucket(wallet,"RESERVED").signum()!=0 || bucket(wallet,"PENDING").signum()!=0)) {
                throw new ConflictException("Mock and non-mock funds cannot be mixed in settlement");
            }
            if (!cardFunds && "MOCK".equals(wallet.getFundingMode()) && !"MOCK".equalsIgnoreCase(selectedGateway == null ? paymentProvider : selectedGateway.name())) {
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
        if(cardFunds)affected.forEach(w -> w.setFundingMode(explicitFunding));
        if(mockPosting && op.getFundingMode()==null)op.setFundingMode("MOCK");
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
    private void requireLegacyBooking(Long jobId) {
        if(bookingRequests.findByJobId(jobId).isPresent())
            throw new ConflictException("Use the booking-request confirmation and completion flow for this booking");
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
    private boolean hasFunds(Wallet wallet){return bucket(wallet,"AVAILABLE").signum()!=0 || bucket(wallet,"RESERVED").signum()!=0 || bucket(wallet,"PENDING").signum()!=0;}
    private boolean matches(BigDecimal value, BigDecimal expected) { return value != null && value.compareTo(expected) == 0; }
    private BigDecimal bucket(Wallet w, String bucket) {
        BigDecimal value = switch (bucket) { case "AVAILABLE" -> w.getAvailableBalance(); case "RESERVED" -> w.getReservedBalance(); case "PENDING" -> w.getPendingBalance(); default -> throw new IllegalStateException("Invalid bucket"); };
        if (value == null) throw new ConflictException("Legacy wallet needs ledger migration"); return value;
    }
    private void setBucket(Wallet w, String bucket, BigDecimal value) {
        switch (bucket) { case "AVAILABLE" -> w.setAvailableBalance(value); case "RESERVED" -> w.setReservedBalance(value); case "PENDING" -> w.setPendingBalance(value); default -> throw new IllegalStateException(); }
    }
    private Balance balance(Wallet w) { return new Balance(w.getWalletId(), w.getCurrencyCode(), w.getAvailableBalance(), w.getReservedBalance(), w.getPendingBalance(), w.getStatus(), w.getFundingMode()); }
    private Operation response(WalletOperation op) { return new Operation(op.getOperationId(),op.getType(),op.getJobId(),op.getCurrencyCode(),op.getAmount(),op.getStatus(),
            op.getPlatformFeePercentage(),op.getPlatformFee(),op.getProviderEarnings(),op.getFundingMode(),op.getPaymentReference()); }
    private record Posting(Wallet wallet, String bucket, BigDecimal delta) {}
}

