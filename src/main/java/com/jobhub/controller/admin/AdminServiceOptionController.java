package com.jobhub.controller.admin;

import com.jobhub.dto.provider.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ServiceOptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminServiceOptionController {

    private final ServiceOptionService service;

    @PostMapping("/provider-types/{serviceTypeId}/options")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ServiceOptionResponse> create(
            @PathVariable Long serviceTypeId,
            @Valid @RequestBody CreateServiceOptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(serviceTypeId, request));
    }

    @GetMapping("/provider-types/{serviceTypeId}/options")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<ServiceOptionResponse>> list(@PathVariable Long serviceTypeId) {
        return ResponseEntity.ok(service.list(serviceTypeId, true));
    }

    @PutMapping("/provider-types/{serviceTypeId}/options/{optionId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ServiceOptionResponse> update(
            @PathVariable Long serviceTypeId, @PathVariable Long optionId,
            @Valid @RequestBody CreateServiceOptionRequest request) {
        return ResponseEntity.ok(service.update(serviceTypeId, optionId, request));
    }

    @PatchMapping("/provider-types/{serviceTypeId}/options/{optionId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ServiceOptionResponse> updateStatus(
            @PathVariable Long serviceTypeId, @PathVariable Long optionId,
            @Valid @RequestBody UpdateServiceStatusRequest request) {
        return ResponseEntity.ok(service.updateStatus(serviceTypeId, optionId, request.status()));
    }

    @GetMapping("/provider-service-options")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'VERIFICATION_ADMIN')")
    public ResponseEntity<Page<ProviderServiceOptionResponse>> reviewQueue(
            @RequestParam(defaultValue = "PENDING") String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.reviewQueue(status, pageable));
    }

    @PatchMapping("/provider-service-options/{providerOptionId}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'VERIFICATION_ADMIN')")
    public ResponseEntity<ProviderServiceOptionResponse> review(
            @AuthenticationPrincipal AuthenticatedUser reviewer,
            @PathVariable Long providerOptionId,
            @Valid @RequestBody DocumentReviewRequest request) {
        return ResponseEntity.ok(service.review(reviewer.id(), providerOptionId, request));
    }
}
