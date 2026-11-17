package com.jobhub.controller.provider;

import com.jobhub.dto.job.NoShowClaimApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.NoShowClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Provider: report that the customer did not show up / could not be reached on a time-based booking. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/providers/me/bookings/{jobId}/no-show-claim")
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderNoShowClaimController {
    private final NoShowClaimService service;

    /** Whether the complaint button can be enabled now, and from/until when. */
    @GetMapping("/eligibility")
    public Eligibility eligibility(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return service.eligibility(user.id(), jobId);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Claim submit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId, @Valid @RequestBody Submit body) {
        return service.submit(user.id(), jobId, body);
    }

    @GetMapping
    public Claim get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long jobId) {
        return service.providerClaim(user.id(), jobId);
    }
}
