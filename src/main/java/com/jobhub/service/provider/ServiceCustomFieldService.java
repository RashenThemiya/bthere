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
    private static final Set<String> REUSE_SCOPES = Set.of("ASSIGNMENT", "SERVICE", "PROVIDER");
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
    private final ProviderProfileFields profileFields;

    @Transactional
    public ServiceCustomFieldResponse create(Long serviceTypeId, ServiceCustomFieldRequest request) {
        requireType(serviceTypeId);
        ValidatedDefinition definition = validateDefinition(serviceTypeId, null, request);
        validateUniqueCode(serviceTypeId, null, definition);
        ServiceCustomField item = new ServiceCustomField();
        item.setServiceProviderTypeId(serviceTypeId);
        item.setVersion(1);
        item.setStatus("ACTIVE");
        apply(item, request, definition);
        item = fieldRepository.save(item);
        if (item.isProfileBacked()) refreshProfileAssignments(serviceTypeId);
        return response(item);
    }

    @Transactional
    public ServiceCustomFieldResponse update(
            Long serviceTypeId, Long fieldId, ServiceCustomFieldRequest request) {
        ServiceCustomField item = requireField(serviceTypeId, fieldId);
        boolean wasProfileBacked = item.isProfileBacked();
        ValidatedDefinition definition = validateDefinition(serviceTypeId, item, request);
        validateUniqueCode(serviceTypeId, fieldId, definition);
        apply(item, request, definition);
        item = fieldRepository.save(item);
        synchronizeExistingSubmissionIdentity(item);
        if (wasProfileBacked || item.isProfileBacked()) refreshProfileAssignments(serviceTypeId);
        return response(item);
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
        item = fieldRepository.save(item);
        if (item.isProfileBacked()) refreshProfileAssignments(serviceTypeId);
        return response(item);
    }

    @Transactional(readOnly = true)
    public List<ServiceCustomFieldResponse> listAdmin(Long serviceTypeId) {
        requireType(serviceTypeId);
        return fieldRepository.findAllByServiceProviderTypeIdOrderByDisplayOrderAscLabelAsc(serviceTypeId)
                .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceCustomFieldResponse> listForOption(
            Long serviceTypeId, Long optionId, boolean includeInactive) {
        return fieldRepository.findAllByServiceProviderTypeIdOrderByDisplayOrderAscLabelAsc(serviceTypeId)
                .stream().filter(field -> includeInactive || "ACTIVE".equals(field.getStatus()))
                .filter(field -> field.appliesToOptions(Set.of(optionId)))
                .map(this::response).toList();
    }

    @Transactional
    public void saveForOption(Long serviceTypeId, Long optionId,
                              List<OptionDynamicFieldRequest> requests) {
        if (requests == null) return;
        Set<Long> updatedIds = new HashSet<>();
        for (OptionDynamicFieldRequest entry : requests) {
            ServiceCustomFieldRequest definition = entry.definition();
            ServiceCustomField existing = null;
            if (entry.fieldId() != null) {
                if (!updatedIds.add(entry.fieldId())) {
                    throw new IllegalArgumentException("Dynamic fields cannot contain duplicate field IDs");
                }
                existing = requireField(serviceTypeId, entry.fieldId());
                if (!existing.appliesToOptions(Set.of(optionId))) {
                    throw new IllegalArgumentException("Dynamic field does not belong to this option");
                }
            }
            if (definition.optionId() == null && definition.optionIds() == null) {
                definition = definition.withOptionIds(existing == null
                        ? List.of(optionId) : List.copyOf(existing.effectiveOptionIds()));
            }
            Set<Long> targetOptions = requestedOptionIds(definition);
            if (!targetOptions.isEmpty() && !targetOptions.contains(optionId)) {
                throw new IllegalArgumentException("Dynamic field must apply to the edited option");
            }
            if (existing == null) create(serviceTypeId, definition);
            else update(serviceTypeId, entry.fieldId(), definition);
        }
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
                .filter(item -> item.effectiveOptionIds().isEmpty()).map(this::response).toList();
        List<ServiceDynamicSchemaResponse.OptionSchema> optionSchemas = optionRepository
                .findAllByServiceProviderTypeIdAndStatusOrderByDisplayOrderAscNameAsc(
                        serviceTypeId, "ACTIVE").stream()
                .filter(option -> selectedOptionId == null
                        || selectedOptionId.equals(option.getOptionId()))
                .map(option -> new ServiceDynamicSchemaResponse.OptionSchema(
                        option.getOptionId(), option.getCode(), option.getName(),
                        fields.stream().filter(field -> field.effectiveOptionIds().contains(option.getOptionId()))
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
            submission.setProviderId(assignment.getServiceProviderId());
            submission.setServiceProviderTypeId(assignment.getServiceProviderTypeId());
            submission.setReusableCode(field.getCode());
            submission.setReusableFieldType(field.getFieldType());
            submission.setReuseScope(normalizedReuseScope(field));
            submission.setValueJson(write(entry.value()));
            submission.setVerificationStatus(field.isRequiresApproval() ? "PENDING" : "APPROVED");
            submission.setReviewedBy(null);
            submission.setReviewedAt(null);
            submission.setReviewNote(null);
            submissionRepository.save(submission);
        }
        approvalService.refreshProviderApprovals(assignment.getServiceProviderId());
        return providerSubmissions(assignment);
    }

    @Transactional(readOnly = true)
    public List<ProviderCustomSubmissionResponse> getSubmissions(Long userId, Long assignmentId) {
        return providerSubmissions(ownedAssignment(userId, assignmentId));
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
        if (!field.appliesToOptions(selectedOptionIds)) {
            throw new IllegalArgumentException("Select the related service option before submitting this field");
        }
        if (field.isProfileBacked()) {
            throw new IllegalArgumentException("This field uses your provider profile; update the profile instead of submitting a duplicate answer");
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
        if (Set.of("SELECT", "MULTI_SELECT").contains(field.getFieldType())) {
            JsonNode choices = read(field.getOptionsJson());
            List<JsonNode> selected = new ArrayList<>();
            if ("SELECT".equals(field.getFieldType())) selected.add(value);
            else value.forEach(selected::add);
            for (JsonNode selection : selected) {
                if (!DynamicFieldChoices.contains(choices, selection)) {
                    throw new IllegalArgumentException("Select a configured choice for " + field.getLabel());
                }
            }
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
        ProviderProfileFields.validate(request, scope, fieldType);
        if (request.customerVisible() || request.searchable()) {
            if (!PROVIDER_REQUIREMENT.equals(scope)
                    || !Set.of("SELECT", "MULTI_SELECT", "NUMBER", "BOOLEAN").contains(fieldType)
                    || request.condition() != null && !request.condition().isNull()) {
                throw new IllegalArgumentException("Customer fields must be unconditional SELECT, MULTI_SELECT, NUMBER or BOOLEAN provider requirements");
            }
        }
        if (request.searchable() && !request.customerVisible()) {
            throw new IllegalArgumentException("Searchable fields must be customer visible");
        }
        String expectedOperator = "NUMBER".equals(fieldType) ? "RANGE"
                : "BOOLEAN".equals(fieldType) ? "EQUALS" : "IN";
        if (request.filterOperator() != null
                && (!request.searchable() || !expectedOperator.equals(request.filterOperator()))) {
            throw new IllegalArgumentException("Invalid filter operator for this field type");
        }
        if (!FIELD_TYPES.contains(fieldType)) {
            throw new IllegalArgumentException("Unsupported custom field type");
        }
        Set<Long> optionIds = requestedOptionIds(request);
        for (Long optionId : optionIds) {
            ServiceOption option = optionRepository.findByOptionIdAndServiceProviderTypeId(optionId, serviceTypeId)
                    .orElseThrow(() -> new IllegalArgumentException("Service option is invalid"));
            if (!option.activeForService(serviceTypeId)) {
                throw new IllegalArgumentException("Service option must be active");
            }
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
        if (Set.of("SELECT", "MULTI_SELECT").contains(fieldType)) {
            DynamicFieldChoices.validateDefinition(request.options());
        }
        String reuseScope = request.reuseScope() == null || request.reuseScope().isBlank()
                ? "ASSIGNMENT" : request.reuseScope().trim().toUpperCase(Locale.ROOT);
        if (!REUSE_SCOPES.contains(reuseScope)) {
            throw new IllegalArgumentException(
                    "Reuse scope must be ASSIGNMENT, SERVICE or PROVIDER");
        }
        if (BOOKING_FIELD.equals(scope) && !"ASSIGNMENT".equals(reuseScope)) {
            throw new IllegalArgumentException("Booking fields must use ASSIGNMENT reuse scope");
        }
        return new ValidatedDefinition(scope, normalizeCode(request.code()), fieldType, reuseScope, optionIds);
    }

    private void apply(ServiceCustomField item, ServiceCustomFieldRequest request,
                       ValidatedDefinition definition) {
        item.setOptionIds(new LinkedHashSet<>(definition.optionIds()));
        item.setOptionId(definition.optionIds().size() == 1
                ? definition.optionIds().iterator().next() : null);
        item.setScope(definition.scope());
        item.setReuseScope(definition.reuseScope());
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
        item.setIconJson(write(request.icon()));
        item.setValueSource(request.valueSource() == null ? "MANUAL" : request.valueSource());
        item.setProfileField(request.profileField());
        item.setCustomerVisible(request.customerVisible());
        item.setSearchable(request.searchable());
        item.setFilterOperator(!request.searchable() ? null : "NUMBER".equals(definition.fieldType())
                ? "RANGE" : "BOOLEAN".equals(definition.fieldType()) ? "EQUALS" : "IN");
    }

    private List<ProviderCustomSubmissionResponse> providerSubmissions(
            ServiceProviderTypeAssignment assignment) {
        List<ProviderCustomFieldSubmission> exact = submissionRepository
                .findAllByAssignmentId(assignment.getAssignmentId());
        Map<Long, ProviderCustomFieldSubmission> byField = new HashMap<>();
        exact.forEach(item -> byField.put(item.getFieldId(), item));
        Set<Long> selectedOptions = providerOptionRepository
                .findAllByAssignmentId(assignment.getAssignmentId()).stream()
                .map(ProviderServiceOption::getOptionId)
                .collect(java.util.stream.Collectors.toSet());
        List<ServiceCustomField> fields = fieldRepository
                .findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(
                        assignment.getServiceProviderTypeId(), PROVIDER_REQUIREMENT, "ACTIVE")
                .stream().filter(field -> field.appliesToOptions(selectedOptions)).toList();
        List<ProviderCustomSubmissionResponse> result = new ArrayList<>();
        for (ServiceCustomField field : fields) {
            ProviderCustomFieldSubmission own = byField.remove(field.getFieldId());
            if (field.isProfileBacked()) {
                var resolved = profileFields.resolve(assignment.getServiceProviderId(), field);
                result.add(new ProviderCustomSubmissionResponse(null, assignment.getAssignmentId(), field.getFieldId(),
                        field.getCode(), field.getLabel(), resolved.value(), resolved.status(), null, null, null,
                        normalizedReuseScope(field), false, null, "PROVIDER_PROFILE", field.getProfileField(), true));
                continue;
            }
            if (own != null) {
                result.add(submissionResponse(own, field, false,
                        assignment.getAssignmentId(), own.getAssignmentId()));
                continue;
            }
            findReusableSubmission(assignment, field).ifPresent(source ->
                    result.add(submissionResponse(source, field, true,
                            assignment.getAssignmentId(), source.getAssignmentId())));
        }
        byField.values().stream().map(this::submissionResponse).forEach(result::add);
        return result;
    }

    private ProviderCustomSubmissionResponse submissionResponse(ProviderCustomFieldSubmission item) {
        ServiceCustomField field = fieldRepository.findById(item.getFieldId()).orElse(null);
        return submissionResponse(item, field, false,
                item.getAssignmentId(), item.getAssignmentId());
    }

    private ProviderCustomSubmissionResponse submissionResponse(
            ProviderCustomFieldSubmission item, ServiceCustomField targetField,
            boolean reused, Long targetAssignmentId, Long sourceAssignmentId) {
        return new ProviderCustomSubmissionResponse(item.getSubmissionId(), targetAssignmentId,
                targetField == null ? item.getFieldId() : targetField.getFieldId(),
                targetField == null ? null : targetField.getCode(),
                targetField == null ? null : targetField.getLabel(), read(item.getValueJson()),
                item.getVerificationStatus(), item.getReviewedBy(), item.getReviewedAt(), item.getReviewNote(),
                targetField == null ? normalizedReuseScope(item) : normalizedReuseScope(targetField),
                reused, sourceAssignmentId, targetField == null ? "MANUAL" : targetField.effectiveValueSource(),
                targetField == null ? null : targetField.getProfileField(), targetField != null && targetField.isProfileBacked());
    }

    private ServiceCustomFieldResponse response(ServiceCustomField item) {
        return new ServiceCustomFieldResponse(item.getFieldId(), item.getServiceProviderTypeId(),
                item.getOptionId(), List.copyOf(item.effectiveOptionIds()), item.getScope(), normalizedReuseScope(item), item.getCode(), item.getLabel(),
                item.getDescription(), item.getFieldType(), item.isRequired(),
                item.isRequiresApproval(), item.getMinimumFiles(), item.getMaximumFiles(),
                item.getMaximumFileSizeMb(), readStringList(item.getAllowedFileTypesJson()),
                read(item.getOptionsJson()), read(item.getConditionJson()), item.getDisplayOrder(),
                item.getVersion(), item.getStatus(), item.isCustomerVisible(), item.isSearchable(), item.getFilterOperator(),
                readIcon(item.getIconJson()), item.effectiveValueSource(), item.getProfileField());
    }

    private ServiceMediaPayload readIcon(String value) {
        if (value == null) return null;
        try { return objectMapper.readValue(value, ServiceMediaPayload.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored field icon is invalid", exception); }
    }

    /** Resolves exact answers before reusable answers; callers must allowlist public fields. */
    @Transactional(readOnly = true)
    public List<ProviderCustomSubmissionResponse> resolvedProviderAnswers(ServiceProviderTypeAssignment assignment) {
        return providerSubmissions(assignment);
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

    private Optional<ProviderCustomFieldSubmission> findReusableSubmission(
            ServiceProviderTypeAssignment assignment, ServiceCustomField field) {
        String reuseScope = normalizedReuseScope(field);
        if ("ASSIGNMENT".equals(reuseScope)) return Optional.empty();
        return submissionRepository
                .findAllByProviderIdAndReusableCodeAndVerificationStatusOrderByUpdatedAtDesc(
                        assignment.getServiceProviderId(), field.getCode(), "APPROVED")
                .stream()
                .filter(item -> field.getFieldType().equals(item.getReusableFieldType()))
                .filter(item -> "PROVIDER".equals(reuseScope)
                        || assignment.getServiceProviderTypeId()
                        .equals(item.getServiceProviderTypeId()))
                .findFirst();
    }

    private void synchronizeExistingSubmissionIdentity(ServiceCustomField field) {
        List<ProviderCustomFieldSubmission> submissions = submissionRepository
                .findAllByFieldId(field.getFieldId());
        for (ProviderCustomFieldSubmission submission : submissions) {
            ServiceProviderTypeAssignment assignment = assignmentRepository
                    .findById(submission.getAssignmentId()).orElse(null);
            if (assignment == null) continue;
            submission.setProviderId(assignment.getServiceProviderId());
            submission.setServiceProviderTypeId(assignment.getServiceProviderTypeId());
            submission.setReusableCode(field.getCode());
            submission.setReusableFieldType(field.getFieldType());
            submission.setReuseScope(normalizedReuseScope(field));
        }
        submissionRepository.saveAll(submissions);
    }

    private void refreshProfileAssignments(Long serviceTypeId) {
        assignmentRepository.findAllByServiceProviderTypeId(serviceTypeId).stream()
                .map(ServiceProviderTypeAssignment::getServiceProviderId).distinct()
                .forEach(approvalService::refreshProviderApprovals);
    }

    private String normalizedReuseScope(ServiceCustomField field) {
        return field.getReuseScope() == null ? "ASSIGNMENT" : field.getReuseScope();
    }

    private String normalizedReuseScope(ProviderCustomFieldSubmission submission) {
        return submission.getReuseScope() == null ? "ASSIGNMENT" : submission.getReuseScope();
    }

    private Set<Long> requestedOptionIds(ServiceCustomFieldRequest request) {
        if (request.optionId() != null && request.optionIds() != null) {
            throw new IllegalArgumentException("Provide optionIds or optionId, not both");
        }
        List<Long> ids = request.optionIds() != null ? request.optionIds()
                : request.optionId() == null ? List.of() : List.of(request.optionId());
        Set<Long> result = new LinkedHashSet<>(ids);
        if (result.size() != ids.size() || result.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Option IDs must be positive and unique");
        }
        return result;
    }

    private void validateUniqueCode(Long serviceTypeId, Long fieldId, ValidatedDefinition definition) {
        boolean conflict = fieldRepository.findAllByServiceProviderTypeIdOrderByDisplayOrderAscLabelAsc(serviceTypeId)
                .stream().filter(field -> !Objects.equals(fieldId, field.getFieldId()))
                .filter(field -> definition.scope().equals(field.getScope())
                        && definition.code().equalsIgnoreCase(field.getCode()))
                .anyMatch(field -> field.effectiveOptionIds().isEmpty() || definition.optionIds().isEmpty()
                        || field.effectiveOptionIds().stream().anyMatch(definition.optionIds()::contains));
        if (conflict) throw new ConflictException("Custom field code already exists for these options");
    }

    private record ValidatedDefinition(
            String scope, String code, String fieldType, String reuseScope, Set<Long> optionIds) {}
}
