package com.jobhub.service.provider;

import com.jobhub.dto.admin.AdminProviderRegistrationApi.*;
import com.jobhub.dto.provider.*;
import com.jobhub.dto.upload.FileUploadResponse;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.repository.provider.ServiceProviderTypeAssignmentRepository;
import com.jobhub.service.audit.AuditService;
import com.jobhub.service.upload.S3ImageUploadService;
import com.jobhub.service.upload.S3PrivateDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProviderRegistrationService {
    private final ServiceProviderRepository providers;
    private final ServiceProviderTypeAssignmentRepository assignments;
    private final ProviderServiceRegistrationService registrations;
    private final ProviderTypeService types;
    private final ProviderOnboardingService onboarding;
    private final ProviderDocumentService documents;
    private final ServiceCustomFieldService fields;
    private final ProviderQualificationService qualifications;
    private final ServiceOptionService options;
    private final ProviderAvailabilityService availability;
    private final S3PrivateDocumentService storage;
    private final S3ImageUploadService images;
    private final AuditService audit;

    public Context context(Long providerId) {
        var provider = provider(providerId);
        return new Context(providerId, types.getAssignments(provider.getUserId()),
                onboarding.get(provider.getUserId()), documents.getProviderDocuments(provider.getUserId()));
    }

    @Transactional
    public ProviderServiceRegistrationResponse register(Long actorId, Long providerId, Registration request) {
        var provider = providers.lockById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
        requireAccount(provider);
        var result = registrations.register(provider.getUserId(), request.registration());
        audit.record(actorId, "ADMIN_PROVIDER_SERVICE_REGISTRATION", "PROVIDER_SERVICE_ASSIGNMENT",
                result.assignment().assignmentId(), null,
                Map.of("providerId", providerId, "source", request.source(), "note", note(request.note())));
        return result;
    }

    @Transactional
    public FileUploadResponse upload(Long actorId, Long providerId, MultipartFile file, boolean image, Source source) {
        var provider = provider(providerId);
        var result = image ? images.uploadImage("providers/" + provider.getUserId() + "/images", file)
                : storage.upload(provider.getUserId(), file);
        audit.record(actorId, "ADMIN_PROVIDER_FILE_UPLOAD", "SERVICE_PROVIDER", providerId, null,
                Map.of("key", result.key(), "source", source));
        return result;
    }

    @Transactional
    public ProviderDocumentResponse addDocument(Long actorId, Long providerId, Document request) {
        var result = documents.addDocument(provider(providerId).getUserId(), request.document());
        audit.record(actorId, "ADMIN_PROVIDER_DOCUMENT_SUBMITTED", "PROVIDER_DOCUMENT", result.documentId(), null,
                Map.of("providerId", providerId, "source", request.source(), "note", note(request.note())));
        return result;
    }

    @Transactional
    public List<ProviderCustomSubmissionResponse> requirements(Long actorId, Long providerId, Long assignmentId,
                                                              ProviderCustomSubmissionRequest request) {
        var provider = assignedProvider(providerId, assignmentId);
        var result = fields.saveSubmissions(provider.getUserId(), assignmentId, request);
        recordEdit(actorId, providerId, assignmentId, "ADMIN_PROVIDER_REQUIREMENTS_SUBMITTED");
        return result;
    }

    @Transactional
    public ProfessionalCertificateResponse certificate(Long actorId, Long providerId, Long assignmentId,
                                                        ProfessionalCertificateRequest request) {
        var provider = assignedProvider(providerId, assignmentId);
        var result = qualifications.addCertificate(provider.getUserId(), assignmentId, request);
        recordEdit(actorId, providerId, assignmentId, "ADMIN_PROVIDER_CERTIFICATE_SUBMITTED");
        return result;
    }

    @Transactional
    public EducationQualificationResponse education(Long actorId, Long providerId, Long assignmentId,
                                                     EducationQualificationRequest request) {
        var provider = assignedProvider(providerId, assignmentId);
        var result = qualifications.addEducation(provider.getUserId(), assignmentId, request);
        recordEdit(actorId, providerId, assignmentId, "ADMIN_PROVIDER_EDUCATION_SUBMITTED");
        return result;
    }

    @Transactional
    public List<ProviderServiceOptionResponse> options(Long actorId, Long providerId, Long assignmentId,
                                                      UpdateProviderOptionsRequest request) {
        var provider = assignedProvider(providerId, assignmentId);
        var result = options.replaceProviderOptions(provider.getUserId(), assignmentId, request);
        recordEdit(actorId, providerId, assignmentId, "ADMIN_PROVIDER_OPTIONS_UPDATED");
        return result;
    }

    @Transactional
    public List<String> languages(Long actorId, Long providerId, Long assignmentId, UpdateProviderLanguagesRequest request) {
        var provider = assignedProvider(providerId, assignmentId);
        var result = options.replaceLanguages(provider.getUserId(), assignmentId, request);
        recordEdit(actorId, providerId, assignmentId, "ADMIN_PROVIDER_LANGUAGES_UPDATED");
        return result;
    }

    @Transactional
    public List<ProviderSkillResponse> skills(Long actorId, Long providerId, Long assignmentId, UpdateProviderSkillsRequest request) {
        var provider = assignedProvider(providerId, assignmentId);
        var result = qualifications.replaceSkills(provider.getUserId(), assignmentId, request);
        recordEdit(actorId, providerId, assignmentId, "ADMIN_PROVIDER_SKILLS_UPDATED");
        return result;
    }

    @Transactional
    public ProviderScheduleResponse schedule(Long actorId, Long providerId, Long assignmentId, ProviderScheduleRequest request) {
        var provider = assignedProvider(providerId, assignmentId);
        var result = availability.replaceSchedule(provider.getUserId(), assignmentId, request);
        recordEdit(actorId, providerId, assignmentId, "ADMIN_PROVIDER_SCHEDULE_UPDATED");
        return result;
    }

    @Transactional
    public List<ProviderServiceAreaResponse> areas(Long actorId, Long providerId, Long assignmentId,
            Long optionId, String deliveryMode, List<ProviderServiceAreaRequest> request) {
        var provider = assignedProvider(providerId, assignmentId);
        var result = availability.replaceServiceAreas(provider.getUserId(), assignmentId, optionId, deliveryMode, request);
        recordEdit(actorId, providerId, assignmentId, "ADMIN_PROVIDER_SERVICE_AREAS_UPDATED");
        return result;
    }

    private ServiceProvider assignedProvider(Long providerId, Long assignmentId) {
        var provider = provider(providerId);
        assignments.findByAssignmentIdAndServiceProviderId(assignmentId, providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider service assignment not found"));
        return provider;
    }
    private ServiceProvider provider(Long providerId) {
        var provider = providers.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
        requireAccount(provider);
        return provider;
    }
    private void requireAccount(ServiceProvider provider) {
        if (provider.getUserId() == null) throw new IllegalArgumentException("Provider must have a user account");
    }
    private String note(String note) { return note == null ? "" : note.trim(); }
    private void recordEdit(Long actorId, Long providerId, Long assignmentId, String action) {
        audit.record(actorId, action, "PROVIDER_SERVICE_ASSIGNMENT", assignmentId, null, Map.of("providerId", providerId));
    }
}
