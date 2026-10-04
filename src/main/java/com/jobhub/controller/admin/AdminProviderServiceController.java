package com.jobhub.controller.admin;

import com.jobhub.dto.provider.ProviderTypeAssignmentResponse;
import com.jobhub.dto.provider.UpdateProviderServiceStatusRequest;
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
}
