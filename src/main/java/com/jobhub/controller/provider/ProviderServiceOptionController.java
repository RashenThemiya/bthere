package com.jobhub.controller.provider;

import com.jobhub.dto.provider.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ServiceOptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/providers/me/services/{assignmentId}")
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderServiceOptionController {
    private final ServiceOptionService service;

    @GetMapping("/options")
    public ResponseEntity<List<ProviderServiceOptionResponse>> getOptions(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId) {
        return ResponseEntity.ok(service.getProviderOptions(user.id(), assignmentId));
    }

    @PutMapping("/options")
    public ResponseEntity<List<ProviderServiceOptionResponse>> replaceOptions(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId,
            @Valid @RequestBody UpdateProviderOptionsRequest request) {
        return ResponseEntity.ok(service.replaceProviderOptions(user.id(), assignmentId, request));
    }

    @GetMapping("/languages")
    public ResponseEntity<List<String>> getLanguages(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId) {
        return ResponseEntity.ok(service.getLanguages(user.id(), assignmentId));
    }

    @PutMapping("/languages")
    public ResponseEntity<List<String>> replaceLanguages(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId,
            @Valid @RequestBody UpdateProviderLanguagesRequest request) {
        return ResponseEntity.ok(service.replaceLanguages(user.id(), assignmentId, request));
    }
}
