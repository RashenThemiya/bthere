package com.jobhub.service.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.provider.*;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ServiceCustomFieldService {

    public static final String PROVIDER_REQUIREMENT = "PROVIDER_REQUIREMENT";
    public static final String BOOKING_FIELD = "BOOKING_FIELD";
    private static final Set<String> SCOPES = Set.of(PROVIDER_REQUIREMENT, BOOKING_FIELD);
    private static final Set<String> FIELD_TYPES = Set.of(
            "TEXT", "TEXTAREA", "NUMBER", "BOOLEAN", "DATE", "TIME", "PHONE", "EMAIL",
            "SELECT", "MULTI_SELECT", "IMAGE_UPLOAD", "DOCUMENT_UPLOAD", "FILE_UPLOAD");

    private final ObjectMapper objectMapper;
    private final ServiceProviderTypeRepository typeRepository;
    private final ServiceOptionRepository optionRepository;
    private final ServiceCustomFieldRepository fieldRepository;
    private final ProviderCustomFieldSubmissionRepository submissionRepository;
    private final ProviderServiceOptionRepository providerOptionRepository;
    private final ServiceProviderTypeAssignmentRepository assignmentRepository;
    private final ProviderProfileService profileService;
    private final ProviderServiceApprovalService approvalService;
    private final ServiceSetupService serviceSetupService;

    @Transactional
    public ServiceCustomFieldResponse create(Long serviceTypeId, ServiceCustomFieldRequest request) {
        requireType(serviceTypeId);
        ValidatedDefinition definition = validateDefinition(serviceTypeId, null, request);
        if (fieldRepository.existsByServiceProviderTypeIdAndScopeAndOptionIdAndCodeIgnoreCase(
                serviceTypeId, definition.scope(), request.optionId(), definition.code())) {
            throw new ConflictException("Custom field code already exists in this scope");
        }
        ServiceCustomField item = new ServiceCustomField();
        item.setServiceProviderTypeId(serviceTypeId);
        item.setVersion(1);
        item.setStatus("ACTIVE");
        apply(item, request, definition);
        return response(fieldRepository.save(item));
    }

    @Transactional
    public ServiceCustomFieldResponse update(
            Long serviceTypeId, Long fieldId, ServiceCustomFieldRequest request) {
        ServiceCustomField item = requireField(serviceTypeId, fieldId);
        ValidatedDefinition definition = validateDefinition(serviceTypeId, item, request);
        boolean identityChanged = !item.getScope().equals(definition.scope())
                || !Objects.equals(item.getOptionId(), request.optionId())
                || !item.getCode().equalsIgnoreCase(definition.code());
        if (identityChanged && fieldRepository
                .existsByServiceProviderTypeIdAndScopeAndOptionIdAndCodeIgnoreCase(
                        serviceTypeId, definition.scope(), request.optionId(), definition.code())) {
            throw new ConflictException("Custom field code already exists in this scope");
        }
        apply(item, request, definition);
        return response(fieldRepository.save(item));
    }

    @Transactional
    public ServiceCustomFieldResponse updateStatus(
            Long serviceTypeId, Long fieldId, String requestedStatus) {
        String status = requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }
        ServiceCustomField item = requireField(serviceTypeId, fieldId);
        item.setStatus(status);
        item.setVersion((item.getVersion() == null ? 1 : item.getVersion()) + 1);
        return response(fieldRepository.save(item));
    }

    @Transactional(readOnly = true)
    public List<ServiceCustomFieldResponse> listAdmin(Long serviceTypeId) {
        requireType(serviceTypeId);
        return fieldRepository.findAllByServiceProviderTypeIdOrderByDisplayOrderAscLabelAsc(serviceTypeId)
                .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public ServiceDynamicSchemaResponse schema(Long serviceTypeId, String scope) {
        return schema(serviceTypeId, scope, null);
    }

    @Transactional(readOnly = true)
    public ServiceDynamicSchemaResponse schema(
            Long serviceTypeId, String scope, Long selectedOptionId) {
        ServiceProviderType type = requireType(serviceTypeId);
        String normalizedScope = normalizeScope(scope);
        List<ServiceCustomField> fields = fieldRepository
                .findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(
                        serviceTypeId, normalizedScope, "ACTIVE");
        List<ServiceCustomFieldResponse> common = fields.stream()
                .filter(item -> item.getOptionId() == null).map(this::response).toList();
        List<ServiceDynamicSchemaResponse.OptionSchema> optionSchemas = optionRepository
                .findAllByServiceProviderTypeIdAndStatusOrderByDisplayOrderAscNameAsc(
                        serviceTypeId, "ACTIVE").stream()
                .filter(option -> selectedOptionId == null
                        || selectedOptionId.equals(option.getOptionId()))
                .map(option -> new ServiceDynamicSchemaResponse.OptionSchema(
                        option.getOptionId(), option.getCode(), option.getName(),
                        fields.stream().filter(field -> option.getOptionId().equals(field.getOptionId()))
                                .map(this::response).toList()))
                .toList();
        if (selectedOptionId != null && optionSchemas.isEmpty()) {
            throw new ResourceNotFoundException("Active service option not found");
        }
        return new ServiceDynamicSchemaResponse(
                serviceTypeId, type.getName(), serviceSetupService.get(serviceTypeId),
                common, optionSchemas);
    }

    @Transactional
    public List<ProviderCustomSubmissionResponse> saveSubmissions(
            Long userId, Long assignmentId, ProviderCustomSubmissionRequest request) {
        ServiceProviderTypeAssignment assignment = ownedAssignment(userId, assignmentId);
        Set<Long> ids = new HashSet<>();
        request.submissions().forEach(entry -> {
            if (!ids.add(entry.fieldId())) {
                throw new IllegalArgumentException("Custom field submissions cannot contain duplicates");
            }
        });
        Map<Long, ServiceCustomField> fields = new HashMap<>();
        fieldRepository.findAllByFieldIdIn(ids).forEach(item -> fields.put(item.getFieldId(), item));
        if (fields.size() != ids.size()) {
            throw new IllegalArgumentException("One or more custom fields do not exist");
        }
        Set<Long> selectedOptionIds = providerOptionRepository.findAllByAssignmentId(assignmentId)
                .stream().map(ProviderServiceOption::getOptionId).collect(java.util.stream.Collectors.toSet());
        for (ProviderCustomSubmissionRequest.Entry entry : request.submissions()) {
            ServiceCustomField field = fields.get(entry.fieldId());
            validateProviderField(userId, assignment, selectedOptionIds, field, entry.value());
            ProviderCustomFieldSubmission submission = submissionRepository
                    .findByAssignmentIdAndFieldId(assignmentId, field.getFieldId())
                    .orElseGet(ProviderCustomFieldSubmission::new);
            submission.setAssignmentId(assignmentId);
            submission.setFieldId(field.getFieldId());
            submission.setValueJson(write(entry.value()));
            submission.setVerificationStatus(field.isRequiresApproval() ? "PENDING" : "APPROVED");
            submission.setReviewedBy(null);
            submission.setReviewedAt(null);
            submission.setReviewNote(null);
            submissionRepository.save(submission);
        }
        approvalService.refreshProviderApprovals(assignment.getServiceProviderId());
        return providerSubmissions(assignmentId);
    }

    @Transactional(readOnly = true)
    public List<ProviderCustomSubmissionResponse> getSubmissions(Long userId, Long assignmentId) {
        ownedAssignment(userId, assignmentId);
        return providerSubmissions(assignmentId);
    }

    @Transactional(readOnly = true)
    public Page<ProviderCustomSubmissionResponse> reviewQueue(String status, Pageable pageable) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "APPROVED", "REJECTED").contains(normalized)) {
            throw new IllegalArgumentException("Status must be PENDING, APPROVED or REJECTED");
        }
        return submissionRepository.findAllByVerificationStatus(normalized, pageable)
                .map(this::submissionResponse);
    }

    @Transactional
    public ProviderCustomSubmissionResponse review(
            Long reviewerId, Long submissionId, DocumentReviewRequest request) {
        ProviderCustomFieldSubmission item = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider submission not found"));
        ServiceCustomField field = fieldRepository.findById(item.getFieldId())
                .orElseThrow(() -> new ResourceNotFoundException("Custom field not found"));
        if (!field.isRequiresApproval()) {
            throw new IllegalArgumentException("This field does not require admin approval");
        }
        item.setVerificationStatus(request.status().trim().toUpperCase(Locale.ROOT));
        item.setReviewedBy(reviewerId);
        item.setReviewedAt(LocalDateTime.now());
        item.setReviewNote(trim(request.rejectionReason()));
        item = submissionRepository.save(item);
        ServiceProviderTypeAssignment assignment = assignmentRepository.findById(item.getAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider service not found"));
        approvalService.refreshProviderApprovals(assignment.getServiceProviderId());
        return submissionResponse(item);
    }

    private void validateProviderField(Long userId, ServiceProviderTypeAssignment assignment,
                                       Set<Long> selectedOptionIds,
                                       ServiceCustomField field, JsonNode value) {
        if (!assignment.getServiceProviderTypeId().equals(field.getServiceProviderTypeId())
                || !PROVIDER_REQUIREMENT.equals(field.getScope())
                || !"ACTIVE".equals(field.getStatus())) {
            throw new IllegalArgumentException("Custom field is not available for this provider service");
        }
        if (field.getOptionId() != null && !selectedOptionIds.contains(field.getOptionId())) {
            throw new IllegalArgumentException("Select the related service option before submitting this field");
        }
        validateValue(userId, field, value);
    }

    private void validateValue(Long userId, ServiceCustomField field, JsonNode value) {
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(field.getLabel() + " cannot be null");
        }
        switch (field.getFieldType()) {
            case "NUMBER" -> { if (!value.isNumber()) invalidType(field); }
            case "BOOLEAN" -> { if (!value.isBoolean()) invalidType(field); }
            case "MULTI_SELECT" -> { if (!value.isArray()) invalidType(field); }
            case "IMAGE_UPLOAD", "DOCUMENT_UPLOAD", "FILE_UPLOAD" -> validateFiles(userId, field, value);
            default -> { if (!value.isTextual()) invalidType(field); }
        }
    }

    private void validateFiles(Long userId, ServiceCustomField field, JsonNode value) {
        JsonNode files = value.isArray() ? value : value.get("files");
        if (files == null || !files.isArray()) invalidType(field);
        int size = files.size();
        int minimum = field.getMinimumFiles() == null ? (field.isRequired() ? 1 : 0)
                : field.getMinimumFiles();
        if (size < minimum || field.getMaximumFiles() != null && size > field.getMaximumFiles()) {
            throw new IllegalArgumentException(field.getLabel() + " requires between " + minimum
                    + " and " + field.getMaximumFiles() + " files");
        }
        files.forEach(file -> {
            JsonNode key = file.isTextual() ? file : file.get("key");
            if (key == null || !key.isTextual() || key.asText().isBlank()) {
                throw new IllegalArgumentException("Every uploaded file requires an S3 key");
            }
            if (!key.asText().startsWith("providers/" + userId + "/")) {
                throw new IllegalArgumentException("Uploaded file does not belong to this provider");
            }
        });
    }

    private void invalidType(ServiceCustomField field) {
        throw new IllegalArgumentException("Invalid value for " + field.getLabel());
    }

    private ValidatedDefinition validateDefinition(
            Long serviceTypeId, ServiceCustomField existing, ServiceCustomFieldRequest request) {
        String scope = normalizeScope(request.scope());
        String fieldType = request.fieldType().trim().toUpperCase(Locale.ROOT);
        if (!FIELD_TYPES.contains(fieldType)) {
            throw new IllegalArgumentException("Unsupported custom field type");
        }
        if (request.optionId() != null) {
            optionRepository.findByOptionIdAndServiceProviderTypeId(request.optionId(), serviceTypeId)
                    .orElseThrow(() -> new IllegalArgumentException("Service option is invalid"));
        }
        boolean upload = fieldType.endsWith("UPLOAD");
        if (upload) {
            int minimum = request.minimumFiles() == null ? (request.required() ? 1 : 0)
                    : request.minimumFiles();
            int maximum = request.maximumFiles() == null ? 1 : request.maximumFiles();
            if (maximum < minimum) {
                throw new IllegalArgumentException("Maximum files cannot be below minimum files");
            }
        }
        if (("SELECT".equals(fieldType) || "MULTI_SELECT".equals(fieldType))
                && (request.options() == null || !request.options().isArray()
                || request.options().isEmpty())) {
            throw new IllegalArgumentException("Select fields require a non-empty options array");
        }
        return new ValidatedDefinition(scope, normalizeCode(request.code()), fieldType);
    }

    private void apply(ServiceCustomField item, ServiceCustomFieldRequest request,
                       ValidatedDefinition definition) {
        item.setOptionId(request.optionId());
        item.setScope(definition.scope());
        item.setCode(definition.code());
        item.setLabel(request.label().trim());
        item.setDescription(trim(request.description()));
        item.setFieldType(definition.fieldType());
        item.setRequired(request.required());
        item.setRequiresApproval(PROVIDER_REQUIREMENT.equals(definition.scope())
                && request.requiresApproval());
        item.setMinimumFiles(request.minimumFiles());
        item.setMaximumFiles(request.maximumFiles());
        item.setMaximumFileSizeMb(request.maximumFileSizeMb());
        item.setAllowedFileTypesJson(write(request.allowedFileTypes()));
        item.setOptionsJson(write(request.options()));
        item.setConditionJson(write(request.condition()));
        item.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
    }

    private List<ProviderCustomSubmissionResponse> providerSubmissions(Long assignmentId) {
        return submissionRepository.findAllByAssignmentId(assignmentId)
                .stream().map(this::submissionResponse).toList();
    }

    private ProviderCustomSubmissionResponse submissionResponse(ProviderCustomFieldSubmission item) {
        ServiceCustomField field = fieldRepository.findById(item.getFieldId()).orElse(null);
        return new ProviderCustomSubmissionResponse(item.getSubmissionId(), item.getAssignmentId(),
                item.getFieldId(), field == null ? null : field.getCode(),
                field == null ? null : field.getLabel(), read(item.getValueJson()),
                item.getVerificationStatus(), item.getReviewedBy(), item.getReviewedAt(), item.getReviewNote());
    }

    private ServiceCustomFieldResponse response(ServiceCustomField item) {
        return new ServiceCustomFieldResponse(item.getFieldId(), item.getServiceProviderTypeId(),
                item.getOptionId(), item.getScope(), item.getCode(), item.getLabel(),
                item.getDescription(), item.getFieldType(), item.isRequired(),
                item.isRequiresApproval(), item.getMinimumFiles(), item.getMaximumFiles(),
                item.getMaximumFileSizeMb(), readStringList(item.getAllowedFileTypesJson()),
                read(item.getOptionsJson()), read(item.getConditionJson()), item.getDisplayOrder(),
                item.getVersion(), item.getStatus());
    }

    private ServiceProviderTypeAssignment ownedAssignment(Long userId, Long assignmentId) {
        ServiceProvider provider = profileService.findByUserId(userId);
        return assignmentRepository.findByAssignmentIdAndServiceProviderId(
                        assignmentId, provider.getServiceProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider service not found"));
    }

    private ServiceProviderType requireType(Long id) {
        return typeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service type not found"));
    }

    private ServiceCustomField requireField(Long serviceTypeId, Long fieldId) {
        return fieldRepository.findByFieldIdAndServiceProviderTypeId(fieldId, serviceTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Custom field not found"));
    }

    private String normalizeScope(String value) {
        String result = value.trim().toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(result)) throw new IllegalArgumentException("Invalid field scope");
        return result;
    }

    private String normalizeCode(String value) {
        String result = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        if (result.isBlank()) throw new IllegalArgumentException("Field code is invalid");
        return result;
    }

    private String write(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid JSON value"); }
    }

    private JsonNode read(String value) {
        if (value == null) return null;
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored JSON is invalid"); }
    }

    private List<String> readStringList(String value) {
        JsonNode node = read(value);
        if (node == null || !node.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        node.forEach(item -> result.add(item.asText()));
        return result;
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private record ValidatedDefinition(String scope, String code, String fieldType) {}
}
