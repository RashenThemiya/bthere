package com.jobhub.controller.admin;

import com.jobhub.dto.market.*;
import com.jobhub.dto.provider.UpdateServiceStatusRequest;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.market.MarketServiceOfferingService;
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

@RestController
@RequestMapping("/api/v1/admin/market-services")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminMarketServiceController {
    private final MarketServiceOfferingService service;

    @PostMapping
    public ResponseEntity<MarketServiceResponse> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateMarketServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(actor.id(), request));
    }

    @GetMapping
    public ResponseEntity<Page<MarketServiceResponse>> list(
            @RequestParam(required = false) Long marketId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.list(marketId, pageable));
    }

    @PatchMapping("/{offeringId}/status")
    public ResponseEntity<MarketServiceResponse> status(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long offeringId,
            @Valid @RequestBody UpdateServiceStatusRequest request) {
        return ResponseEntity.ok(service.status(actor.id(), offeringId, request.status()));
    }

    @PostMapping("/{offeringId}/rates")
    public ResponseEntity<JobRateResponse> addRate(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long offeringId,
            @Valid @RequestBody CreateJobRateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addRate(actor.id(), offeringId, request));
    }

    @GetMapping("/{offeringId}/rates")
    public ResponseEntity<Page<JobRateResponse>> rates(
            @PathVariable Long offeringId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.rates(offeringId, pageable));
    }

    @PutMapping("/{offeringId}/rates/{rateId}")
    public ResponseEntity<JobRateResponse> updateRate(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long offeringId, @PathVariable Long rateId,
            @Valid @RequestBody UpdateJobRateRequest request) {
        return ResponseEntity.ok(service.updateRate(actor.id(), offeringId, rateId, request));
    }

    @PatchMapping("/{offeringId}/rates/{rateId}/status")
    public ResponseEntity<JobRateResponse> rateStatus(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long offeringId, @PathVariable Long rateId,
            @Valid @RequestBody UpdateServiceStatusRequest request) {
        return ResponseEntity.ok(service.rateStatus(actor.id(), offeringId, rateId, request.status()));
    }
}
