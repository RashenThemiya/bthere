package com.jobhub.controller.admin;

import com.jobhub.dto.provider.ProviderTypeAssignmentResponse;
import com.jobhub.dto.provider.UpdateProviderServiceStatusRequest;
import com.jobhub.dto.provider.EmergencyApprovalRequest;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ProviderTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/admin/provider-service-assignments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminProviderServiceController {

    private final ProviderTypeService providerTypeService;

    @PatchMapping("/{assignmentId}/status")
    public ResponseEntity<ProviderTypeAssignmentResponse> updateStatus(
            @PathVariable Long assignmentId,
            @Valid @RequestBody UpdateProviderServiceStatusRequest request
    ) {
        return ResponseEntity.ok(providerTypeService.updateAdminStatus(
                assignmentId,
                request.status()
        ));
    }

    @PostMapping("/{assignmentId}/emergency-override")
    public ResponseEntity<ProviderTypeAssignmentResponse> emergencyOverride(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long assignmentId,
            @Valid @RequestBody EmergencyApprovalRequest request) {
        return ResponseEntity.ok(providerTypeService.applyEmergencyOverride(
                actor.id(), assignmentId, request));
    }

    @DeleteMapping("/{assignmentId}/emergency-override")
    public ResponseEntity<ProviderTypeAssignmentResponse> revokeEmergencyOverride(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable Long assignmentId) {
        return ResponseEntity.ok(providerTypeService.revokeEmergencyOverride(
                actor.id(), assignmentId));
    }
}
