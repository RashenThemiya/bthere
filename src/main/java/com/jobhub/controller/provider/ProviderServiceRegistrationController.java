package com.jobhub.controller.provider;

import com.jobhub.dto.provider.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ProviderServiceRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/providers/me/service-registrations")
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderServiceRegistrationController {
    private final ProviderServiceRegistrationService service;

    @PostMapping
    public ResponseEntity<ProviderServiceRegistrationResponse> register(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ProviderServiceRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(user.id(), request));
    }
}
