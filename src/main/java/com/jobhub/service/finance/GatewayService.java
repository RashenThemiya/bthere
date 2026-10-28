package com.jobhub.service.finance;

import com.jobhub.dto.customer.GatewayApi.*;
import com.jobhub.entity.finance.*;
import com.jobhub.exception.*;
import com.jobhub.payments.PaymentGateway;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.finance.*;
import com.jobhub.repository.job.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly=true)
public class GatewayService {
    private final PaymentGateway gateway;
    private final GatewayIntentRepository intents;
    private final PaymentInstrumentRepository instruments;
    private final UserRepository users;
    private final CustomerRepository customers;
    private final JobRepository jobs;
    private final WalletService wallets;

    public Settings settings() { return new Settings(gateway.capabilities()); }
    public List<Method> methods(Long userId) { return instruments.findAllByUserIdAndProvider(userId,gateway.name()).stream().map(this::method).toList(); }
    @Transactional
    public Method setup(Long userId,String type) {
        gateway.requireEnabled();
        if (!gateway.isMock()) throw new PaymentUnavailableException("Official hosted setup is not configured");
        if (!Set.of("CARD","BANK_ACCOUNT").contains(type)) throw new IllegalArgumentException("Invalid payment method type");
        lockUser(userId);
        return method(instruments.findByUserIdAndProviderAndType(userId,gateway.name(),type).orElseGet(() -> {
            var m=new PaymentInstrument();m.setUserId(userId);m.setProvider(gateway.name());m.setType(type);
            m.setDisplayLabel("CARD".equals(type)?"TEST CARD •••• 4242":"TEST BANK •••• 0001");m.setStatus("VERIFIED_MOCK");
            if ("CARD".equals(type)) m.setBrand("VISA");
            return instruments.save(m);
        }));
    }
    @Transactional
    public void remove(Long userId,Long methodId) {
        lockUser(userId); // Serializes with startWallet, which locks the user before creating an intent.
        var m=instruments.findById(methodId).filter(x -> userId.equals(x.getUserId()) && gateway.name().equals(x.getProvider()))
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));
        if (intents.existsByMethodIdAndStatus(methodId,"PENDING")) throw new ConflictException("Payment method is used by a pending payment");
        instruments.delete(m);
    }
    @Transactional
    public Intent startWallet(Long userId,String currency,BigDecimal amount,Long methodId,String purpose,String key) {
        gateway.requireEnabled();
        if (!Set.of("TOPUP","WITHDRAWAL").contains(purpose)) throw new IllegalArgumentException("Invalid purpose");
        currency=WalletService.currency(currency);amount=WalletService.money(amount,currency);
        lockUser(userId);wallets.get(userId,currency);
        validateMethod(userId,methodId,"WITHDRAWAL".equals(purpose)?"BANK_ACCOUNT":"CARD","WITHDRAWAL".equals(purpose));
        var intent=create(userId,purpose,currency,amount,null,methodId,key);
        if ("WITHDRAWAL".equals(purpose) && "PENDING".equals(intent.getStatus())) {
            wallets.gatewayMovement(userId,currency,amount,intent.getIntentId(),"WITHDRAW_RESERVE");
        }
        return response(intent);
    }
    @Transactional
    public Intent startCard(Long userId,Long jobId,String key) {
        gateway.requireEnabled();
        validateKey(key);
        var job=jobs.lockPaymentJob(jobId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        var customer=customers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (!customer.getCustomerId().equals(job.getCustomerId()) || !"CARD".equals(job.getPaymentMethod())) throw new ResourceNotFoundException("Card booking not found");
        lockUser(userId);
        var existing=intents.findByUserIdAndProviderAndRequestKey(userId,gateway.name(),hash("BOOKING:CARD_JOB:"+jobId));
        if (existing.isPresent()) return response(existing.get());
        if (!"PENDING".equals(job.getPaymentStatus()) || !Set.of("PENDING","ACCEPTED","CONFIRMED").contains(job.getJobStatus())) throw new ConflictException("Booking is not payable");
        validateKey(key);
        // One intent per booking prevents charging a card twice with different retry keys.
        return response(create(userId,"CARD",WalletService.currency(job.getCurrencyCode()),
                WalletService.money(job.getExpectedAmount(),job.getCurrencyCode()),jobId,null,"CARD_JOB:"+jobId));
    }
    public Intent get(Long userId,Long id) {
        return response(intents.findById(id).filter(i -> userId.equals(i.getUserId()) && gateway.name().equals(i.getProvider()))
                .orElseThrow(() -> new ResourceNotFoundException("Payment intent not found")));
    }
    @Transactional
    public Intent simulate(Long userId,Long id,String outcome) {
        gateway.requireEnabled();
        outcome=gateway.simulate(outcome); // No live adapter may expose this operation.
        // Resolve booking ID without locks, then consistently lock job -> user -> intent.
        var peek=intents.findById(id).filter(i -> userId.equals(i.getUserId()) && gateway.name().equals(i.getProvider()))
                .orElseThrow(() -> new ResourceNotFoundException("Payment intent not found"));
        WalletService.currency(peek.getCurrencyCode());
        var job=peek.getJobId()==null?null:jobs.lockPaymentJob(peek.getJobId()).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        lockUser(userId);
        var i=intents.lockOwned(id,userId).filter(x -> gateway.name().equals(x.getProvider())).orElseThrow(() -> new ResourceNotFoundException("Payment intent not found"));
        String target=switch(outcome) { case "SUCCESS" -> "SUCCEEDED";case "FAILURE" -> "FAILED";case "REFUND" -> "REFUNDED";default -> "PENDING"; };
        if (target.equals(i.getStatus())) return response(i);
        boolean refund="REFUNDED".equals(target);
        if (refund && (!"SUCCEEDED".equals(i.getStatus()) || "WITHDRAWAL".equals(i.getPurpose()))) throw new ConflictException("Payment cannot be refunded in this state");
        if (!refund && !"PENDING".equals(i.getStatus())) throw new ConflictException("Payment already has a terminal outcome");
        if (job!=null) {
            if (!Objects.equals(i.getCurrencyCode(),job.getCurrencyCode()) || i.getAmount().compareTo(job.getExpectedAmount())!=0) throw new ConflictException("Booking payment amount/currency changed");
            if ("SUCCEEDED".equals(target) && !Set.of("PENDING","ACCEPTED","CONFIRMED").contains(job.getJobStatus())) throw new ConflictException("Booking is not payable");
            job.setPaymentStatus(refund?"REFUNDED":"SUCCEEDED".equals(target)?"PAID":"FAILED");jobs.save(job);
        } else if ("TOPUP".equals(i.getPurpose())) {
            if ("SUCCEEDED".equals(target)) wallets.gatewayMovement(userId,i.getCurrencyCode(),i.getAmount(),id,"TOPUP");
            if (refund) wallets.gatewayMovement(userId,i.getCurrencyCode(),i.getAmount(),id,"TOPUP_REFUND");
        } else if ("WITHDRAWAL".equals(i.getPurpose())) {
            if ("SUCCEEDED".equals(target)) wallets.gatewayMovement(userId,i.getCurrencyCode(),i.getAmount(),id,"WITHDRAW_SUCCESS");
            if ("FAILED".equals(target)) wallets.gatewayMovement(userId,i.getCurrencyCode(),i.getAmount(),id,"WITHDRAW_RELEASE");
        }
        i.setStatus(target);return response(intents.save(i));
    }
    private GatewayIntent create(Long userId,String purpose,String currency,BigDecimal amount,Long job,Long method,String key) {
        validateKey(key);
        String fingerprint=hash(purpose+":"+currency+":"+amount.toPlainString()+":"+job+":"+method);
        String requestKey=hash((job==null?"CLIENT:":"BOOKING:")+key);
        var previous=intents.findByUserIdAndProviderAndRequestKey(userId,gateway.name(),requestKey);
        if(previous.isPresent()) {
            if(!fingerprint.equals(previous.get().getRequestHash()))throw new ConflictException("Idempotency key was used for a different request");
            return previous.get();
        }
        var i=new GatewayIntent();i.setUserId(userId);i.setProvider(gateway.name());i.setRequestKey(requestKey);i.setRequestHash(fingerprint);
        i.setPurpose(purpose);i.setCurrencyCode(currency);i.setAmount(amount);i.setJobId(job);i.setMethodId(method);i.setStatus("PENDING");i=intents.save(i);
        i.setStatus(gateway.start(new PaymentGateway.Request(i.getIntentId(),userId,purpose,currency,amount,job,method)));return intents.save(i);
    }
    private void validateMethod(Long userId,Long id,String type,boolean required) {
        if(id==null) { if(required)throw new IllegalArgumentException("Select a verified mock bank method");return; }
        instruments.findById(id).filter(m -> userId.equals(m.getUserId()) && gateway.name().equals(m.getProvider())
                && type.equals(m.getType()) && "VERIFIED_MOCK".equals(m.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Verified payment method not found"));
    }
    private void lockUser(Long id) {users.lockById(id).filter(u -> "ACTIVE".equals(u.getStatus())).orElseThrow(() -> new ConflictException("Inactive account"));}
    private void validateKey(String key) {if(key==null||key.isBlank()||key.length()>100)throw new IllegalArgumentException("Idempotency-Key is required, maximum100characters");}
    private static String hash(String value) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private Method method(PaymentInstrument m) {
        // Mock cards saved before brand existed are the synthetic Visa 4242.
        String brand=m.getBrand()==null && "CARD".equals(m.getType()) && "VERIFIED_MOCK".equals(m.getStatus())?"VISA":m.getBrand();
        return new Method(m.getMethodId(),m.getType(),m.getDisplayLabel(),brand,m.getStatus());
    }
    private Intent response(GatewayIntent i) {return new Intent(i.getIntentId(),i.getPurpose(),i.getCurrencyCode(),i.getAmount(),i.getJobId(),i.getMethodId(),i.getStatus());}
}
