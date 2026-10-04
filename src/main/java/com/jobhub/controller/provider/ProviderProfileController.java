package com.jobhub.controller.provider;

import com.jobhub.dto.provider.ProviderProfileRequest;
import com.jobhub.dto.provider.ProviderProfileResponse;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ProviderProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/providers/me/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderProfileController {

    private final ProviderProfileService providerProfileService;

    @GetMapping
    public ResponseEntity<ProviderProfileResponse> get(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(providerProfileService.get(user.id()));
    }

    @PutMapping
    public ResponseEntity<ProviderProfileResponse> upsert(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ProviderProfileRequest request
    ) {
        return ResponseEntity.ok(providerProfileService.upsert(user.id(), request));
    }
}
