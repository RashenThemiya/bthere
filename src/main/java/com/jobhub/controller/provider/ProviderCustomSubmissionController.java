package com.jobhub.controller.provider;

import com.jobhub.dto.provider.ProviderCustomSubmissionRequest;
import com.jobhub.dto.provider.ProviderCustomSubmissionResponse;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ServiceCustomFieldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/providers/me/services/{assignmentId}/submissions")
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderCustomSubmissionController {
    private final ServiceCustomFieldService service;

    @GetMapping
    public ResponseEntity<List<ProviderCustomSubmissionResponse>> get(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId) {
        return ResponseEntity.ok(service.getSubmissions(user.id(), assignmentId));
    }

    @PutMapping
    public ResponseEntity<List<ProviderCustomSubmissionResponse>> save(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId,
            @Valid @RequestBody ProviderCustomSubmissionRequest request) {
        return ResponseEntity.ok(service.saveSubmissions(user.id(), assignmentId, request));
    }
}
