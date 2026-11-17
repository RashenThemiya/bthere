package com.jobhub.controller.admin;

import com.jobhub.dto.job.NoShowClaimApi.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.NoShowClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Super Admin: review customer no-show complaints, and configure them per service option. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminNoShowClaimController {
    private final NoShowClaimService service;

    @GetMapping("/no-show-claims")
    public Page<Claim> list(@RequestParam(required = false) String status, @RequestParam(required = false) Long providerId,
                            @PageableDefault(size = 20, sort = "claimId", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(status, providerId, pageable);
    }

    /** Includes a 10-minute signed photoUrl. */
    @GetMapping("/no-show-claims/{claimId}")
    public Claim get(@PathVariable Long claimId) { return service.adminClaim(claimId); }

    @PostMapping("/no-show-claims/{claimId}/approve")
    public Claim approve(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long claimId,
                         @Valid @RequestBody Approve body) {
        return service.approve(user.id(), claimId, body);
    }

    @PostMapping("/no-show-claims/{claimId}/reject")
    public Claim reject(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long claimId,
                        @Valid @RequestBody Reject body) {
        return service.reject(user.id(), claimId, body);
    }

    @GetMapping("/service-options/{optionId}/no-show-settings")
    public Settings settings(@PathVariable Long optionId) { return service.settings(optionId); }

    /** Full replacement of the four settings; send noShowClaimAfterMinutes null to turn complaints off. */
    @PutMapping("/service-options/{optionId}/no-show-settings")
    public Settings update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long optionId,
                           @Valid @RequestBody Settings body) {
        return service.updateSettings(user.id(), optionId, body);
    }
}
