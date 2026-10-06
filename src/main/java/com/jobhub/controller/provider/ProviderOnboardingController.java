package com.jobhub.controller.provider;

import com.jobhub.dto.provider.ProviderOnboardingResponse;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ProviderOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/providers/me/completion")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderOnboardingController {
    private final ProviderOnboardingService service;

    @GetMapping
    public ResponseEntity<ProviderOnboardingResponse> get(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(service.get(user.id()));
    }
}
