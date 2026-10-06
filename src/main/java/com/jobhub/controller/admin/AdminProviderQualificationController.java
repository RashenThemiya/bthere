package com.jobhub.controller.admin;

import com.jobhub.dto.provider.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ProviderQualificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

@RestController
@RequestMapping("/api/v1/admin/provider-qualifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'VERIFICATION_ADMIN')")
public class AdminProviderQualificationController {

    private final ProviderQualificationService service;

    @GetMapping("/skills")
    public ResponseEntity<Page<ProviderSkillResponse>> listSkills(
            @RequestParam(defaultValue = "PENDING") String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listSkillsForReview(status, pageable));
    }

    @PatchMapping("/skills/{id}/review")
    public ResponseEntity<ProviderSkillResponse> reviewSkill(
            @AuthenticationPrincipal AuthenticatedUser reviewer, @PathVariable Long id,
            @Valid @RequestBody DocumentReviewRequest request) {
        return ResponseEntity.ok(service.reviewSkill(id, reviewer.id(), request));
    }

    @GetMapping("/certificates")
    public ResponseEntity<Page<ProfessionalCertificateResponse>> listCertificates(
            @RequestParam(defaultValue = "PENDING") String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listCertificatesForReview(status, pageable));
    }

    @PatchMapping("/certificates/{id}/review")
    public ResponseEntity<ProfessionalCertificateResponse> reviewCertificate(
            @AuthenticationPrincipal AuthenticatedUser reviewer, @PathVariable Long id,
            @Valid @RequestBody DocumentReviewRequest request) {
        return ResponseEntity.ok(service.reviewCertificate(id, reviewer.id(), request));
    }

    @GetMapping("/education")
    public ResponseEntity<Page<EducationQualificationResponse>> listEducation(
            @RequestParam(defaultValue = "PENDING") String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listEducationForReview(status, pageable));
    }

    @PatchMapping("/education/{id}/review")
    public ResponseEntity<EducationQualificationResponse> reviewEducation(
            @AuthenticationPrincipal AuthenticatedUser reviewer, @PathVariable Long id,
            @Valid @RequestBody DocumentReviewRequest request) {
        return ResponseEntity.ok(service.reviewEducation(id, reviewer.id(), request));
    }
}
