package com.jobhub.controller.provider;

import com.jobhub.dto.provider.ProviderTypeAssignmentResponse;
import com.jobhub.dto.provider.ProviderTypeAssignmentRequest;
import com.jobhub.dto.provider.ProviderTypeResponse;
import com.jobhub.dto.provider.ServiceDocumentRequirementResponse;
import com.jobhub.dto.provider.ServiceSetupResponse;
import com.jobhub.dto.provider.UpdateProviderTypesRequest;
import com.jobhub.dto.provider.UpdateProviderServiceStatusRequest;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ProviderTypeService;
import com.jobhub.service.provider.ServiceSetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProviderTypeController {

    private final ProviderTypeService providerTypeService;
    private final ServiceSetupService serviceSetupService;

    @GetMapping("/api/v1/provider-types")
    public ResponseEntity<List<ProviderTypeResponse>> listTypes() {
        return ResponseEntity.ok(providerTypeService.listActiveTypes());
    }

    @GetMapping("/api/v1/provider-types/{providerTypeId}/document-requirements")
    public ResponseEntity<List<ServiceDocumentRequirementResponse>> getRequirements(
            @PathVariable Long providerTypeId
    ) {
        return ResponseEntity.ok(
                providerTypeService.getDocumentRequirements(providerTypeId)
        );
    }

    @GetMapping("/api/v1/provider-types/{providerTypeId}/setup")
    public ResponseEntity<ServiceSetupResponse> getServiceSetup(
            @PathVariable Long providerTypeId
    ) {
        return ResponseEntity.ok(serviceSetupService.get(providerTypeId));
    }

    @GetMapping("/api/v1/providers/me/service-types")
    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    public ResponseEntity<List<ProviderTypeAssignmentResponse>> getAssignments(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(providerTypeService.getAssignments(user.id()));
    }

    @PutMapping("/api/v1/providers/me/service-types")
    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    public ResponseEntity<List<ProviderTypeAssignmentResponse>> replaceAssignments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateProviderTypesRequest request
    ) {
        return ResponseEntity.ok(providerTypeService.replaceAssignments(user.id(), request));
    }

    @PostMapping("/api/v1/providers/me/service-types")
    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    public ResponseEntity<ProviderTypeAssignmentResponse> addAssignment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ProviderTypeAssignmentRequest request
    ) {
        return ResponseEntity.ok(providerTypeService.addAssignment(user.id(), request));
    }

    @PatchMapping("/api/v1/providers/me/service-types/{assignmentId}/status")
    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    public ResponseEntity<ProviderTypeAssignmentResponse> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long assignmentId,
            @Valid @RequestBody UpdateProviderServiceStatusRequest request
    ) {
        return ResponseEntity.ok(providerTypeService.updateProviderStatus(
                user.id(),
                assignmentId,
                request.status()
        ));
    }
}
