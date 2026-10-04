package com.jobhub.controller.admin;

import com.jobhub.dto.provider.CreateProviderTypeRequest;
import com.jobhub.dto.provider.ProviderTypeResponse;
import com.jobhub.dto.provider.ServiceDocumentRequirementResponse;
import com.jobhub.dto.provider.ServiceSetupRequest;
import com.jobhub.dto.provider.ServiceSetupResponse;
import com.jobhub.dto.provider.UpdateServiceDocumentRequirementsRequest;
import com.jobhub.dto.provider.UpdateServiceStatusRequest;
import com.jobhub.service.provider.ProviderTypeService;
import com.jobhub.service.provider.ServiceSetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/provider-types")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminProviderTypeController {

    private final ProviderTypeService providerTypeService;
    private final ServiceSetupService serviceSetupService;

    @PostMapping
    public ResponseEntity<ProviderTypeResponse> create(
            @Valid @RequestBody CreateProviderTypeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(providerTypeService.createType(request));
    }

    @PostMapping("/setup")
    public ResponseEntity<ServiceSetupResponse> createWithRequirements(
            @Valid @RequestBody ServiceSetupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serviceSetupService.create(request));
    }

    @PutMapping("/{providerTypeId}/setup")
    public ResponseEntity<ServiceSetupResponse> updateSetup(
            @PathVariable Long providerTypeId,
            @Valid @RequestBody ServiceSetupRequest request
    ) {
        return ResponseEntity.ok(serviceSetupService.update(providerTypeId, request));
    }

    @PatchMapping("/{providerTypeId}/status")
    public ResponseEntity<ServiceSetupResponse> updateStatus(
            @PathVariable Long providerTypeId,
            @Valid @RequestBody UpdateServiceStatusRequest request
    ) {
        return ResponseEntity.ok(
                serviceSetupService.updateStatus(providerTypeId, request.status())
        );
    }

    @PutMapping("/{providerTypeId}/document-requirements")
    public ResponseEntity<List<ServiceDocumentRequirementResponse>> setRequirements(
            @PathVariable Long providerTypeId,
            @Valid @RequestBody UpdateServiceDocumentRequirementsRequest request
    ) {
        return ResponseEntity.ok(
                providerTypeService.replaceDocumentRequirements(
                        providerTypeId,
                        request
                )
        );
    }
}
