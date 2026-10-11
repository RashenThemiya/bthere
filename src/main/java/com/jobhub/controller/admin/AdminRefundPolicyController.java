package com.jobhub.controller.admin;

import com.jobhub.dto.market.RefundPolicyRequest;
import com.jobhub.dto.market.RefundPolicyResponse;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.market.RefundPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/markets/{marketId}/refund-policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminRefundPolicyController {
    private final RefundPolicyService service;

    @PostMapping
    public ResponseEntity<RefundPolicyResponse> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long marketId,
            @Valid @RequestBody RefundPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(actor.id(), marketId, request));
    }

    @GetMapping
    public ResponseEntity<List<RefundPolicyResponse>> list(@PathVariable Long marketId) {
        return ResponseEntity.ok(service.list(marketId));
    }

    @GetMapping("/{policyId}")
    public ResponseEntity<RefundPolicyResponse> get(
            @PathVariable Long marketId, @PathVariable Long policyId) {
        return ResponseEntity.ok(service.get(marketId, policyId));
    }

    @PutMapping("/{policyId}")
    public ResponseEntity<RefundPolicyResponse> update(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long marketId, @PathVariable Long policyId,
            @Valid @RequestBody RefundPolicyRequest request) {
        return ResponseEntity.ok(service.update(actor.id(), marketId, policyId, request));
    }

    @PostMapping("/{policyId}/activate")
    public ResponseEntity<RefundPolicyResponse> activate(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long marketId, @PathVariable Long policyId) {
        return ResponseEntity.ok(service.activate(actor.id(), marketId, policyId));
    }

    @PostMapping("/{policyId}/deactivate")
    public ResponseEntity<RefundPolicyResponse> deactivate(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long marketId, @PathVariable Long policyId) {
        return ResponseEntity.ok(service.deactivate(actor.id(), marketId, policyId));
    }
}
