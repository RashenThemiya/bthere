package com.jobhub.controller.admin;

import com.jobhub.dto.provider.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ServiceCustomFieldService;
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
public class AdminCustomFieldController {
    private final ServiceCustomFieldService service;

    @PostMapping("/provider-types/{serviceTypeId}/custom-fields")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ServiceCustomFieldResponse> create(
            @PathVariable Long serviceTypeId,
            @Valid @RequestBody ServiceCustomFieldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(serviceTypeId, request));
    }

    @GetMapping("/provider-types/{serviceTypeId}/custom-fields")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<ServiceCustomFieldResponse>> list(@PathVariable Long serviceTypeId) {
        return ResponseEntity.ok(service.listAdmin(serviceTypeId));
    }

    @PutMapping("/provider-types/{serviceTypeId}/custom-fields/{fieldId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ServiceCustomFieldResponse> update(
            @PathVariable Long serviceTypeId, @PathVariable Long fieldId,
            @Valid @RequestBody ServiceCustomFieldRequest request) {
        return ResponseEntity.ok(service.update(serviceTypeId, fieldId, request));
    }

    @PatchMapping("/provider-types/{serviceTypeId}/custom-fields/{fieldId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ServiceCustomFieldResponse> updateStatus(
            @PathVariable Long serviceTypeId, @PathVariable Long fieldId,
            @Valid @RequestBody UpdateServiceStatusRequest request) {
        return ResponseEntity.ok(service.updateStatus(serviceTypeId, fieldId, request.status()));
    }

    @GetMapping("/provider-submissions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'VERIFICATION_ADMIN')")
    public ResponseEntity<Page<ProviderCustomSubmissionResponse>> reviewQueue(
            @RequestParam(defaultValue = "PENDING") String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.reviewQueue(status, pageable));
    }

    @PatchMapping("/provider-submissions/{submissionId}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'VERIFICATION_ADMIN')")
    public ResponseEntity<ProviderCustomSubmissionResponse> review(
            @AuthenticationPrincipal AuthenticatedUser reviewer,
            @PathVariable Long submissionId,
            @Valid @RequestBody DocumentReviewRequest request) {
        return ResponseEntity.ok(service.review(reviewer.id(), submissionId, request));
    }
}
