package com.jobhub.controller.provider;

import com.jobhub.dto.job.CancellationApi.PenaltySummary;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.customer.ProviderPenaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The provider's cancellation penalties and the total still owed. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/providers/me/penalties")
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderPenaltyController {
    private final ProviderPenaltyService service;

    @GetMapping
    public PenaltySummary mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.mine(user.id());
    }
}
