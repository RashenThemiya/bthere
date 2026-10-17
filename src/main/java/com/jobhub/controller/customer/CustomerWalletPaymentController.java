package com.jobhub.controller.customer;
import com.jobhub.dto.customer.WalletApi.Operation;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.finance.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/customers/me/bookings/{jobId}")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerWalletPaymentController {
    private final WalletService wallets;
    private final com.jobhub.service.finance.GatewayService gateway;
    @PostMapping("/wallet-payment")
    public Operation reserve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return wallets.reserveBooking(user.id(), jobId);
    }
    @PostMapping("/card-payment-session")
    public com.jobhub.dto.customer.GatewayApi.Intent card(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
            @RequestHeader("Idempotency-Key") String key) {
        return gateway.startCard(user.id(),jobId,key);
    }
}
