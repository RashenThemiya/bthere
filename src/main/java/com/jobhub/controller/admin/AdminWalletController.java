package com.jobhub.controller.admin;
import com.jobhub.dto.customer.WalletApi.Operation;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.finance.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/admin/wallet-bookings/{jobId}")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE_ADMIN')")
public class AdminWalletController {
    private final WalletService wallets;
    @PostMapping("/release")
    public Operation release(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return wallets.releaseCancelledBooking(user.id(), jobId);
    }
    @PostMapping("/settlement")
    public Operation request(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return wallets.requestSettlement(user.id(), jobId);
    }
    @PostMapping("/settlement/approve")
    public Operation approve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return wallets.approveSettlement(user.id(), jobId);
    }
}
