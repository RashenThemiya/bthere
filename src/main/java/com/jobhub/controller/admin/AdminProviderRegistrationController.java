package com.jobhub.controller.admin;

import com.jobhub.dto.admin.AdminProviderRegistrationApi.*;
import com.jobhub.dto.provider.*;
import com.jobhub.dto.upload.FileUploadResponse;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.AdminProviderRegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/admin/providers/{providerId}")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','VERIFICATION_ADMIN')")
public class AdminProviderRegistrationController {
    private final AdminProviderRegistrationService service;

    @GetMapping("/registration-context")
    public Context context(@PathVariable @Positive Long providerId) { return service.context(providerId); }

    @PostMapping("/service-registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderServiceRegistrationResponse register(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable @Positive Long providerId, @Valid @RequestBody Registration request) {
        return service.register(actor.id(), providerId, request);
    }

    @PostMapping(value = "/uploads/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponse uploadDocument(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable @Positive Long providerId, @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "ADMIN_ASSISTED") Source source) {
        return service.upload(actor.id(), providerId, file, false, source);
    }

    @PostMapping(value = "/uploads/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileUploadResponse uploadImage(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable @Positive Long providerId, @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "ADMIN_ASSISTED") Source source) {
        return service.upload(actor.id(), providerId, file, true, source);
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderDocumentResponse document(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable @Positive Long providerId, @Valid @RequestBody Document request) {
        return service.addDocument(actor.id(), providerId, request);
    }

    @PutMapping("/service-registrations/{assignmentId}/requirements")
    public List<ProviderCustomSubmissionResponse> requirements(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable @Positive Long providerId, @PathVariable @Positive Long assignmentId,
            @Valid @RequestBody ProviderCustomSubmissionRequest request) {
        return service.requirements(actor.id(), providerId, assignmentId, request);
    }

    @PostMapping("/service-registrations/{assignmentId}/certificates")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfessionalCertificateResponse certificate(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable @Positive Long providerId, @PathVariable @Positive Long assignmentId,
            @Valid @RequestBody ProfessionalCertificateRequest request) {
        return service.certificate(actor.id(), providerId, assignmentId, request);
    }

    @PostMapping("/service-registrations/{assignmentId}/education")
    @ResponseStatus(HttpStatus.CREATED)
    public EducationQualificationResponse education(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable @Positive Long providerId, @PathVariable @Positive Long assignmentId,
            @Valid @RequestBody EducationQualificationRequest request) {
        return service.education(actor.id(), providerId, assignmentId, request);
    }
    @PutMapping("/service-registrations/{assignmentId}/options")
    public List<ProviderServiceOptionResponse> options(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable @Positive Long providerId, @PathVariable @Positive Long assignmentId,
            @Valid @RequestBody UpdateProviderOptionsRequest request) {
        return service.options(actor.id(), providerId, assignmentId, request);
    }

    @PutMapping("/service-registrations/{assignmentId}/languages")
    public List<String> languages(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable @Positive Long providerId, @PathVariable @Positive Long assignmentId,
            @Valid @RequestBody UpdateProviderLanguagesRequest request) {
        return service.languages(actor.id(), providerId, assignmentId, request);
    }

    @PutMapping("/service-registrations/{assignmentId}/skills")
    public List<ProviderSkillResponse> skills(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable @Positive Long providerId, @PathVariable @Positive Long assignmentId,
            @Valid @RequestBody UpdateProviderSkillsRequest request) {
        return service.skills(actor.id(), providerId, assignmentId, request);
    }

    @PutMapping("/service-registrations/{assignmentId}/availability")
    public ProviderScheduleResponse schedule(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable @Positive Long providerId, @PathVariable @Positive Long assignmentId,
            @Valid @RequestBody ProviderScheduleRequest request) {
        return service.schedule(actor.id(), providerId, assignmentId, request);
    }

    @PutMapping("/service-registrations/{assignmentId}/options/{optionId}/service-areas")
    public List<ProviderServiceAreaResponse> areas(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable @Positive Long providerId, @PathVariable @Positive Long assignmentId,
            @PathVariable @Positive Long optionId, @RequestParam String deliveryMode,
            @Valid @RequestBody @jakarta.validation.constraints.NotNull
            List<@jakarta.validation.constraints.NotNull @Valid ProviderServiceAreaRequest> request) {
        return service.areas(actor.id(), providerId, assignmentId, optionId, deliveryMode, request);
    }

}
