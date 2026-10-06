package com.jobhub.controller.provider;

import com.jobhub.dto.provider.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ProviderQualificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/providers/me/services/{assignmentId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE_PROVIDER')")
public class ProviderQualificationController {

    private final ProviderQualificationService service;

    @GetMapping("/skills")
    public ResponseEntity<List<ProviderSkillResponse>> getSkills(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId) {
        return ResponseEntity.ok(service.getSkills(user.id(), assignmentId));
    }

    @PutMapping("/skills")
    public ResponseEntity<List<ProviderSkillResponse>> replaceSkills(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId,
            @Valid @RequestBody UpdateProviderSkillsRequest request) {
        return ResponseEntity.ok(service.replaceSkills(user.id(), assignmentId, request));
    }

    @GetMapping("/certificates")
    public ResponseEntity<List<ProfessionalCertificateResponse>> getCertificates(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId) {
        return ResponseEntity.ok(service.getCertificates(user.id(), assignmentId));
    }

    @PostMapping("/certificates")
    public ResponseEntity<ProfessionalCertificateResponse> addCertificate(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId,
            @Valid @RequestBody ProfessionalCertificateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addCertificate(user.id(), assignmentId, request));
    }

    @GetMapping("/education")
    public ResponseEntity<List<EducationQualificationResponse>> getEducation(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId) {
        return ResponseEntity.ok(service.getEducation(user.id(), assignmentId));
    }

    @PostMapping("/education")
    public ResponseEntity<EducationQualificationResponse> addEducation(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long assignmentId,
            @Valid @RequestBody EducationQualificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addEducation(user.id(), assignmentId, request));
    }
}
