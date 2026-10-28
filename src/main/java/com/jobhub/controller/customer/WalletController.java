package com.jobhub.controller.customer;

import com.jobhub.dto.customer.WalletApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.finance.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @Validated
@RequestMapping("/api/v1/me")
@PreAuthorize("hasAnyRole('CUSTOMER','SERVICE_PROVIDER')")
public class WalletController {
    private final WalletService wallets;
    private final com.jobhub.service.finance.GatewayService gateway;
    @GetMapping("/wallet-capabilities")
    public Capabilities capabilities() { return gateway.settings().capabilities(); }
    @GetMapping("/payment-settings")
    public com.jobhub.dto.customer.GatewayApi.Settings settings() { return gateway.settings(); }
    @GetMapping("/payment-methods")
    public java.util.List<com.jobhub.dto.customer.GatewayApi.Method> methods(@AuthenticationPrincipal AuthenticatedUser user) { return gateway.methods(user.id()); }
    @GetMapping("/wallets")
    public List<Balance> list(@AuthenticationPrincipal AuthenticatedUser user) { return wallets.list(user.id()); }
    @PutMapping("/wallets/{currency}")
    public Balance open(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String currency) {
        return wallets.open(user.id(), currency);
    }
    @GetMapping("/wallets/{currency}")
    public Balance get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String currency) {
        return wallets.get(user.id(), currency);
    }
    @GetMapping("/wallets/{currency}/transactions")
    public Page<Entry> history(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String currency,
            @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return wallets.history(user.id(), currency, PageRequest.of(page, size));
    }
    @GetMapping("/wallets/{currency}/reconciliation")
    public Reconciliation reconcile(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String currency) {
        return wallets.reconcile(user.id(), currency);
    }
    @PostMapping("/wallets/{currency}/topups")
    public com.jobhub.dto.customer.GatewayApi.Intent topup(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String currency,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=100) String key, @Valid @RequestBody Amount request) {
        return gateway.startWallet(user.id(),currency,request.amount(),request.methodId(),"TOPUP",key);
    }
    @PostMapping("/wallets/{currency}/withdrawals")
    public com.jobhub.dto.customer.GatewayApi.Intent withdraw(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String currency,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max=100) String key, @Valid @RequestBody Amount request) {
        return gateway.startWallet(user.id(),currency,request.amount(),request.methodId(),"WITHDRAWAL",key);
    }
    @PostMapping("/payment-methods/setup")
    public com.jobhub.dto.customer.GatewayApi.Method setup(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam @Pattern(regexp="CARD|BANK_ACCOUNT") String type) {
        return gateway.setup(user.id(),type);
    }
    @DeleteMapping("/payment-methods/{methodId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long methodId) { gateway.remove(user.id(),methodId); }
    @GetMapping("/payment-intents/{id}")
    public com.jobhub.dto.customer.GatewayApi.Intent intent(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id) { return gateway.get(user.id(),id); }
    @PostMapping("/mock-payments/{id}/events")
    public com.jobhub.dto.customer.GatewayApi.Intent simulate(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,
            @Valid @RequestBody com.jobhub.dto.customer.GatewayApi.Event event) { return gateway.simulate(user.id(),id,event.outcome()); }
}
