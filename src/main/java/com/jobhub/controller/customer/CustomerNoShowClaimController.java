package com.jobhub.controller.customer;

import com.jobhub.dto.job.NoShowClaimApi.Claim;
import com.jobhub.dto.job.NoShowClaimApi.CustomerResponse;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.NoShowClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Customer: see a no-show complaint on their booking and add their side before admin decides. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/customers/me/bookings/{jobId}/no-show-claim")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerNoShowClaimController {
    private final NoShowClaimService service;

    @GetMapping
    public Claim get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return service.customerClaim(user.id(), jobId);
    }

    @PostMapping("/response")
    public Claim respond(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId,
                         @Valid @RequestBody CustomerResponse body) {
        return service.respond(user.id(), jobId, body);
    }
}
