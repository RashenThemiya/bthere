package com.jobhub.service.provider;

import com.jobhub.dto.provider.*;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class ProviderQualificationService {

    private static final Set<String> REVIEW_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

    private final ProviderProfileService profileService;
    private final ServiceProviderTypeAssignmentRepository assignmentRepository;
    private final ServiceProviderTypeRepository typeRepository;
    private final ServiceTypeSkillRepository skillRepository;
    private final ProviderServiceSkillRepository providerSkillRepository;
    private final ProviderProfessionalCertificateRepository certificateRepository;
    private final ProviderEducationQualificationRepository educationRepository;
    private final ProviderServiceApprovalService approvalService;

    @Transactional
    public List<ProviderSkillResponse> replaceSkills(
            Long userId, Long assignmentId, UpdateProviderSkillsRequest request
    ) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        Set<Long> ids = new HashSet<>(request.skillIds());
        if (ids.size() != request.skillIds().size()) {
            throw new IllegalArgumentException("Skills cannot contain duplicates");
        }
        Map<Long, ServiceTypeSkill> configured = configuredSkills(assignment);
        if (!configured.keySet().containsAll(ids)) {
            throw new IllegalArgumentException("One or more skills are not configured for this service");
        }

        providerSkillRepository.deleteAllByAssignmentId(assignmentId);
        providerSkillRepository.flush();
        for (Long id : request.skillIds()) {
            ServiceTypeSkill skill = configured.get(id);
            ProviderServiceSkill selected = new ProviderServiceSkill();
            selected.setAssignmentId(assignmentId);
            selected.setSkillId(id);
            selected.setVerificationStatus(skill.isRequiresApproval() ? "PENDING" : "APPROVED");
            providerSkillRepository.save(selected);
        }
        approvalService.refreshProviderApprovals(assignment.getServiceProviderId());
        return skillResponses(assignmentId, configured);
    }

    @Transactional(readOnly = true)
    public List<ProviderSkillResponse> getSkills(Long userId, Long assignmentId) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        return skillResponses(assignmentId, configuredSkills(assignment));
    }

    @Transactional
    public ProfessionalCertificateResponse addCertificate(
            Long userId, Long assignmentId, ProfessionalCertificateRequest request
    ) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        ServiceProviderType type = requireType(assignment.getServiceProviderTypeId());
        String level = normalizeLevel(type.getCertificateRequirement());
        if ("DISABLED".equals(level)) {
            throw new IllegalArgumentException("Certificates are disabled for this service");
        }
        validateDates(request.issuedDate(), request.expiryDate());
        validateOwnedDocumentKey(userId, request.documentKey(), true);

        ProviderProfessionalCertificate item = new ProviderProfessionalCertificate();
        item.setAssignmentId(assignmentId);
        item.setName(request.name().trim());
        item.setIssuingOrganization(request.issuingOrganization().trim());
        item.setCertificateNumber(optional(request.certificateNumber()));
        item.setIssuedDate(request.issuedDate());
        item.setExpiryDate(request.expiryDate());
        item.setDocumentKey(request.documentKey().trim());
        item.setVerificationStatus(type.isCertificateRequiresApproval() ? "PENDING" : "APPROVED");
        item = certificateRepository.save(item);
        approvalService.refreshProviderApprovals(assignment.getServiceProviderId());
        return certificateResponse(item);
    }

    @Transactional(readOnly = true)
    public List<ProfessionalCertificateResponse> getCertificates(Long userId, Long assignmentId) {
        ownedAssignment(userId, assignmentId);
        return certificateRepository.findAllByAssignmentIdOrderByCreatedAtDesc(assignmentId)
                .stream().map(this::certificateResponse).toList();
    }

    @Transactional
    public EducationQualificationResponse addEducation(
            Long userId, Long assignmentId, EducationQualificationRequest request
    ) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        ServiceProviderType type = requireType(assignment.getServiceProviderTypeId());
        String level = normalizeLevel(type.getEducationRequirement());
        if ("DISABLED".equals(level)) {
            throw new IllegalArgumentException("Education qualifications are disabled for this service");
        }
        validateDates(request.startDate(), request.completionDate());
        validateOwnedDocumentKey(userId, request.documentKey(), false);

        ProviderEducationQualification item = new ProviderEducationQualification();
        item.setAssignmentId(assignmentId);
        item.setQualificationName(request.qualificationName().trim());
        item.setInstituteName(request.instituteName().trim());
        item.setFieldOfStudy(optional(request.fieldOfStudy()));
        item.setStartDate(request.startDate());
        item.setCompletionDate(request.completionDate());
        item.setResult(optional(request.result()));
        item.setDocumentKey(optional(request.documentKey()));
        item.setVerificationStatus(type.isEducationRequiresApproval() ? "PENDING" : "APPROVED");
        item = educationRepository.save(item);
        approvalService.refreshProviderApprovals(assignment.getServiceProviderId());
        return educationResponse(item);
    }

    @Transactional(readOnly = true)
    public List<EducationQualificationResponse> getEducation(Long userId, Long assignmentId) {
        ownedAssignment(userId, assignmentId);
        return educationRepository.findAllByAssignmentIdOrderByCreatedAtDesc(assignmentId)
                .stream().map(this::educationResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<ProviderSkillResponse> listSkillsForReview(String status, Pageable pageable) {
        String value = reviewStatus(status);
        return providerSkillRepository.findAllByVerificationStatus(value, pageable)
                .map(item -> {
                    ServiceTypeSkill skill = skillRepository.findById(item.getSkillId()).orElse(null);
                    return skillResponse(item, skill);
                });
    }

    @Transactional(readOnly = true)
    public Page<ProfessionalCertificateResponse> listCertificatesForReview(String status, Pageable pageable) {
        return certificateRepository.findAllByVerificationStatus(reviewStatus(status), pageable)
                .map(this::certificateResponse);
    }

    @Transactional(readOnly = true)
    public Page<EducationQualificationResponse> listEducationForReview(String status, Pageable pageable) {
        return educationRepository.findAllByVerificationStatus(reviewStatus(status), pageable)
                .map(this::educationResponse);
    }

    @Transactional
    public ProviderSkillResponse reviewSkill(Long id, Long reviewerId, DocumentReviewRequest request) {
        ProviderServiceSkill item = providerSkillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider skill was not found"));
        applyReview(item, reviewerId, request);
        item = providerSkillRepository.save(item);
        refreshByAssignment(item.getAssignmentId());
        return skillResponse(item, skillRepository.findById(item.getSkillId()).orElse(null));
    }

    @Transactional
    public ProfessionalCertificateResponse reviewCertificate(
            Long id, Long reviewerId, DocumentReviewRequest request
    ) {
        ProviderProfessionalCertificate item = certificateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate was not found"));
        applyReview(item, reviewerId, request);
        item = certificateRepository.save(item);
        refreshByAssignment(item.getAssignmentId());
        return certificateResponse(item);
    }

    @Transactional
    public EducationQualificationResponse reviewEducation(
            Long id, Long reviewerId, DocumentReviewRequest request
    ) {
        ProviderEducationQualification item = educationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Education qualification was not found"));
        applyReview(item, reviewerId, request);
        item = educationRepository.save(item);
        refreshByAssignment(item.getAssignmentId());
        return educationResponse(item);
    }

    private ServiceProviderTypeAssignment ownedAssignment(Long userId, Long assignmentId) {
        ServiceProvider provider = profileService.findByUserId(userId);
        return assignmentRepository.findByAssignmentIdAndServiceProviderId(
                        assignmentId, provider.getServiceProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider service assignment was not found"));
    }

    private Map<Long, ServiceTypeSkill> configuredSkills(ServiceProviderTypeAssignment assignment) {
        Map<Long, ServiceTypeSkill> result = new HashMap<>();
        skillRepository.findAllByServiceProviderTypeIdOrderByNameAsc(
                        assignment.getServiceProviderTypeId())
                .forEach(item -> result.put(item.getSkillId(), item));
        return result;
    }

    private List<ProviderSkillResponse> skillResponses(Long assignmentId, Map<Long, ServiceTypeSkill> skills) {
        return providerSkillRepository.findAllByAssignmentId(assignmentId).stream()
                .map(item -> skillResponse(item, skills.get(item.getSkillId())))
                .toList();
    }

    private ProviderSkillResponse skillResponse(ProviderServiceSkill item, ServiceTypeSkill skill) {
        return new ProviderSkillResponse(item.getProviderSkillId(), item.getAssignmentId(),
                item.getSkillId(), skill == null ? null : skill.getName(),
                skill != null && skill.isMandatory(), item.getVerificationStatus(),
                item.getRejectionReason());
    }

    private ProfessionalCertificateResponse certificateResponse(ProviderProfessionalCertificate item) {
        return new ProfessionalCertificateResponse(item.getCertificateId(), item.getAssignmentId(),
                item.getName(), item.getIssuingOrganization(), item.getCertificateNumber(),
                item.getIssuedDate(), item.getExpiryDate(), item.getDocumentKey(),
                item.getVerificationStatus(), item.getRejectionReason());
    }

    private EducationQualificationResponse educationResponse(ProviderEducationQualification item) {
        return new EducationQualificationResponse(item.getEducationId(), item.getAssignmentId(),
                item.getQualificationName(), item.getInstituteName(), item.getFieldOfStudy(),
                item.getStartDate(), item.getCompletionDate(), item.getResult(), item.getDocumentKey(),
                item.getVerificationStatus(), item.getRejectionReason());
    }

    private void applyReview(Object object, Long reviewerId, DocumentReviewRequest request) {
        if ("REJECTED".equals(request.status())
                && (request.rejectionReason() == null || request.rejectionReason().isBlank())) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        String reason = "REJECTED".equals(request.status()) ? request.rejectionReason().trim() : null;
        LocalDateTime now = LocalDateTime.now();
        if (object instanceof ProviderServiceSkill item) {
            item.setVerificationStatus(request.status()); item.setVerifiedBy(reviewerId);
            item.setVerifiedAt(now); item.setRejectionReason(reason);
        } else if (object instanceof ProviderProfessionalCertificate item) {
            item.setVerificationStatus(request.status()); item.setVerifiedBy(reviewerId);
            item.setVerifiedAt(now); item.setRejectionReason(reason);
        } else if (object instanceof ProviderEducationQualification item) {
            item.setVerificationStatus(request.status()); item.setVerifiedBy(reviewerId);
            item.setVerifiedAt(now); item.setRejectionReason(reason);
        }
    }

    private void refreshByAssignment(Long assignmentId) {
        assignmentRepository.findById(assignmentId)
                .ifPresent(item -> approvalService.refreshProviderApprovals(item.getServiceProviderId()));
    }

    private ServiceProviderType requireType(Long id) {
        return typeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider type was not found"));
    }

    private String reviewStatus(String status) {
        String value = status.trim().toUpperCase();
        if (!REVIEW_STATUSES.contains(value)) {
            throw new IllegalArgumentException("Status must be PENDING, APPROVED or REJECTED");
        }
        return value;
    }

    private String normalizeLevel(String value) { return value == null ? "OPTIONAL" : value; }
    private String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
    }

    private void validateOwnedDocumentKey(Long userId, String key, boolean required) {
        if (key == null || key.isBlank()) {
            if (required) throw new IllegalArgumentException("Document key is required");
            return;
        }
        String marker = "providers/" + userId + "/documents/";
        if (!key.contains(marker) || key.contains("..")) {
            throw new IllegalArgumentException("Document key does not belong to this provider");
        }
    }
}
