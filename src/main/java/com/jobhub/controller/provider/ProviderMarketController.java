package com.jobhub.controller.provider;

import com.jobhub.dto.provider.ProviderProfileResponse;
import com.jobhub.dto.provider.UpdateProviderMarketRequest;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ProviderProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/providers/me/markets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderMarketController {

    private final ProviderProfileService providerProfileService;

    @PutMapping
    public ResponseEntity<ProviderProfileResponse> updateMarket(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateProviderMarketRequest request
    ) {
        return ResponseEntity.ok(
                providerProfileService.updateMarket(user.id(), request.marketId())
        );
    }
}
