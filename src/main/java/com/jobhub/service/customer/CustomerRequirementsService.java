package com.jobhub.service.customer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.customer.CustomerRequirementsApi.*;
import com.jobhub.entity.customer.*;
import com.jobhub.exception.*;
import com.jobhub.repository.customer.*;
import com.jobhub.repository.provider.*;
import com.jobhub.service.upload.S3PrivateDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerRequirementsService {
    private static final Set<String> TYPES = Set.of("TEXT", "TEXTAREA", "NUMBER", "BOOLEAN", "DATE",
            "TIME", "PHONE", "EMAIL", "SELECT", "MULTI_SELECT", "IMAGE_UPLOAD", "DOCUMENT_UPLOAD", "FILE_UPLOAD");
    private static final Set<String> MODES = Set.of("NONE", "ADMIN_REVIEW", "DOCUMENT_REVIEW");
    private static final Map<String, String> FILE_TYPES = Map.of(
            "application/pdf", "PDF", "image/jpeg", "JPG", "image/png", "PNG", "image/webp", "WEBP");
    private final CustomerRequirementRepository requirements;
    private final CustomerRequirementSubmissionRepository submissions;
    private final CustomerEligibilityRuleRepository rules;
    private final CustomerRepository customers;
    private final ServiceProviderTypeRepository services;
    private final ServiceOptionRepository options;
    private final S3PrivateDocumentService files;
    private final ObjectMapper mapper;

    @Transactional
    public Requirement create(Definition definition) {
        if (requirements.findByCode(definition.code()).isPresent()) {
            throw new ConflictException("Requirement code already exists; add targets to reuse it");
        }
        var entity = new CustomerRequirement();
        entity.setDefinitionVersion(1);
        entity.setStatus("ACTIVE");
        apply(entity, definition);
        return response(requirements.save(entity));
    }

    @Transactional
    public Requirement update(Long id, Definition definition) {
        var entity = requirement(id);
        if (!entity.getCode().equals(definition.code())) {
            throw new IllegalArgumentException("Requirement code is immutable");
        }
        apply(entity, definition);
        entity.setDefinitionVersion(entity.getDefinitionVersion() + 1);
        for (var answer : submissions.findAllByRequirementId(id)) {
            if (!"NONE".equals(entity.getVerificationMode())) {
                answer.setStatus("PENDING");
                submissions.save(answer);
            }
            invalidateDependents(answer.getCustomerId(), entity.getCode());
        }
        return response(requirements.save(entity));
    }

    public List<Requirement> list() {
        return requirements.findAll().stream().map(this::response).toList();
    }

    @Transactional
    public Requirement status(Long id, String status) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new IllegalArgumentException("Invalid status");
        var entity = requirement(id);
        entity.setStatus(status);
        return response(requirements.save(entity));
    }

    private void apply(CustomerRequirement entity, Definition d) {
        if (!TYPES.contains(d.fieldType()) || !MODES.contains(d.verificationMode())) {
            throw new IllegalArgumentException("Unsupported field type or verification mode");
        }
        if (d.blocksBooking() && !d.required()) {
            throw new IllegalArgumentException("A booking-blocking requirement must be required");
        }
        Set<String> targets = new HashSet<>();
        for (var target : d.targets()) {
            validateTarget(target);
            if (!targets.add(targetKey(target))) throw new IllegalArgumentException("Duplicate target");
        }
        if (d.targets().size() > 1 && d.targets().stream().anyMatch(t -> "GLOBAL".equals(t.scope()))) {
            throw new IllegalArgumentException("GLOBAL already applies everywhere; do not combine it with other targets");
        }
        if (new HashSet<>(d.evidenceCodes()).size() != d.evidenceCodes().size()) {
            throw new IllegalArgumentException("Duplicate evidence code");
        }
        if ("DOCUMENT_REVIEW".equals(d.verificationMode()) && !upload(d.fieldType()) && d.evidenceCodes().isEmpty()) {
            throw new IllegalArgumentException("Document review requires a document field or evidence codes");
        }
        if (!"DOCUMENT_REVIEW".equals(d.verificationMode()) && !d.evidenceCodes().isEmpty()) {
            throw new IllegalArgumentException("Evidence codes require DOCUMENT_REVIEW");
        }
        for (String code : d.evidenceCodes()) {
            var evidence = activeCode(code);
            if (code.equals(d.code()) || !upload(evidence.getFieldType())
                    || !"DOCUMENT_REVIEW".equals(evidence.getVerificationMode())
                    || !strings(evidence.getEvidenceCodesJson()).isEmpty()) {
                throw new IllegalArgumentException("Evidence must reference another document-review upload without dependencies");
            }
        }
        if (requirements.findAll().stream().anyMatch(r -> strings(r.getEvidenceCodesJson()).contains(d.code()))
                && (!upload(d.fieldType()) || !"DOCUMENT_REVIEW".equals(d.verificationMode()) || !d.evidenceCodes().isEmpty())) {
            throw new IllegalArgumentException("Referenced evidence must remain a document-review upload without dependencies");
        }
        if (upload(d.fieldType())) {
            int minimum = d.minimumFiles() == null ? 1 : d.minimumFiles();
            int maximum = d.maximumFiles() == null ? 1 : d.maximumFiles();
            if (maximum < minimum) throw new IllegalArgumentException("Maximum files is below minimum files");
            if (!FILE_TYPES.values().containsAll(d.allowedFileTypes())) {
                throw new IllegalArgumentException("Allowed file types: PDF, JPG, PNG, WEBP");
            }
            if ("IMAGE_UPLOAD".equals(d.fieldType()) && d.allowedFileTypes().contains("PDF")) {
                throw new IllegalArgumentException("Image fields cannot accept PDF");
            }
        }
        if (("SELECT".equals(d.fieldType()) || "MULTI_SELECT".equals(d.fieldType()))
                && (d.choices().isEmpty() || new HashSet<>(d.choices()).size() != d.choices().size())) {
            throw new IllegalArgumentException("Selection fields need unique choices");
        }
        // Age-rule references cannot silently become incompatible after definition changes.
        for (var rule : rules.findAllByEnabledTrue()) {
            if (d.code().equals(rule.getDateOfBirthCode()) && (!"DATE".equals(d.fieldType())
                    || rule.isRequiresVerifiedDateOfBirth() && "NONE".equals(d.verificationMode()))) {
                throw new IllegalArgumentException("Definition is used by an active age rule");
            }
        }
        entity.setCode(d.code()); entity.setLabel(d.label()); entity.setDescription(d.description());
        entity.setFieldType(d.fieldType()); entity.setRequired(d.required());
        entity.setVerificationMode(d.verificationMode()); entity.setBlocksBooking(d.blocksBooking());
        entity.setTargetsJson(json(d.targets())); entity.setEvidenceCodesJson(json(d.evidenceCodes()));
        entity.setChoicesJson(json(d.choices())); entity.setAllowedFileTypesJson(json(d.allowedFileTypes()));
        entity.setMinimumFiles(d.minimumFiles()); entity.setMaximumFiles(d.maximumFiles());
        entity.setMaximumFileSizeMb(d.maximumFileSizeMb());
        entity.setDisplayOrder(d.displayOrder() == null ? 0 : d.displayOrder());
    }

    public List<FormItem> form(Long userId, Long serviceId, Long optionId) {
        validateContext(serviceId, optionId);
        var customer = customer(userId);
        var definitions = applicable(serviceId, optionId);
        var answers = answers(customer.getCustomerId());
        var all = activeDefinitions();
        return definitions.stream().map(r -> new FormItem(response(r),
                submissionResponse(answers.get(r.getRequirementId()), r, answers, all))).toList();
    }

    @Transactional
    public List<Submission> submit(Long userId, Submit request) {
        var customer = customer(userId);
        var existing = answers(customer.getCustomerId());
        var active = activeDefinitions();
        Set<Long> ids = new HashSet<>();
        // Validate the whole batch before mutating managed entities.
        for (var entry : request.submissions()) {
            if (!ids.add(entry.requirementId())) throw new IllegalArgumentException("Duplicate requirement ID");
            var r = active.values().stream().filter(x -> x.getRequirementId().equals(entry.requirementId()))
                    .findFirst().orElseThrow(() -> new ResourceNotFoundException("Active customer requirement not found"));
            validateValue(userId, r, entry.value());
            if (entry.expiresOn() != null && entry.expiresOn().isBefore(today())) {
                throw new IllegalArgumentException("Evidence expiry date cannot be in the past");
            }
        }
        List<CustomerRequirementSubmission> saved = new ArrayList<>();
        for (var entry : request.submissions()) {
            var r = requirement(entry.requirementId());
            var s = existing.getOrDefault(r.getRequirementId(), new CustomerRequirementSubmission());
            s.setCustomerId(customer.getCustomerId()); s.setRequirementId(r.getRequirementId());
            s.setValueJson(json(entry.value())); s.setExpiresOn(entry.expiresOn());
            s.setDefinitionVersion(r.getDefinitionVersion());
            s.setAnswerVersion(s.getAnswerVersion() == null ? 1 : s.getAnswerVersion() + 1);
            s.setStatus("NONE".equals(r.getVerificationMode()) ? "PROVIDED" : "PENDING");
            s.setReviewedAt(null); s.setReviewedBy(null); s.setRejectionReason(null);
            s.setEvidenceVersionsJson("{}");
            s = submissions.save(s); existing.put(r.getRequirementId(), s); saved.add(s);
            invalidateDependents(customer.getCustomerId(), r.getCode());
        }
        existing = answers(customer.getCustomerId());
        var currentAnswers = existing;
        return saved.stream().map(s -> submissionResponse(s, requirement(s.getRequirementId()), currentAnswers, active)).toList();
    }

    public Page<Submission> queue(String status, Pageable pageable) {
        if (!Set.of("PENDING", "APPROVED", "REJECTED", "PROVIDED").contains(status)) {
            throw new IllegalArgumentException("Invalid review status");
        }
        return submissions.findAllByStatus(status, pageable).map(s -> submissionResponse(s,
                requirement(s.getRequirementId()), answers(s.getCustomerId()), activeDefinitions()));
    }

    @Transactional
    public Submission review(Long reviewerId, Long id, Review request) {
        var s = submissions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Submission not found"));
        var r = requirement(s.getRequirementId());
        if (!Objects.equals(s.getAnswerVersion(), request.answerVersion())) {
            throw new ConflictException("Customer answer changed; read the submission again before reviewing");
        }
        if (!"ACTIVE".equals(r.getStatus()) || !Objects.equals(s.getDefinitionVersion(), r.getDefinitionVersion())) {
            throw new IllegalArgumentException("Customer must resubmit the current requirement definition");
        }
        if ("NONE".equals(r.getVerificationMode())) throw new IllegalArgumentException("This field does not require review");
        if (!Set.of("APPROVED", "REJECTED").contains(request.status())) throw new IllegalArgumentException("Invalid review status");
        if ("REJECTED".equals(request.status()) && (request.rejectionReason() == null || request.rejectionReason().isBlank())) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        var all = activeDefinitions();
        var answers = answers(s.getCustomerId());
        if ("APPROVED".equals(request.status())) {
            if (expired(s)) throw new IllegalArgumentException("Cannot approve expired evidence");
            Map<String, Integer> evidenceVersions = new LinkedHashMap<>();
            for (String code : strings(r.getEvidenceCodesJson())) {
                var evidence = all.get(code);
                var answer = evidence == null ? null : answers.get(evidence.getRequirementId());
                if (answer == null || !"APPROVED".equals(effectiveStatus(answer, evidence, answers, all))) {
                    throw new IllegalArgumentException("Approve current supporting evidence first: " + code);
                }
                evidenceVersions.put(code, answer.getAnswerVersion());
            }
            s.setEvidenceVersionsJson(json(evidenceVersions));
        }
        s.setStatus(request.status()); s.setReviewedBy(reviewerId); s.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        s.setRejectionReason("REJECTED".equals(request.status()) ? request.rejectionReason().trim() : null);
        s = submissions.save(s);
        if ("REJECTED".equals(request.status())) invalidateDependents(s.getCustomerId(), r.getCode());
        answers.put(s.getRequirementId(), s);
        return submissionResponse(s, r, answers, all);
    }

    @Transactional
    public Rule configureRule(Target target, AgeRule request) {
        validateTarget(target);
        if (request.enabled()) {
            if (request.minimumAge() == null && request.requiredCodes().isEmpty()) {
                throw new IllegalArgumentException("Enabled rule requires minimumAge or requiredCodes");
            }
            if (new HashSet<>(request.requiredCodes()).size() != request.requiredCodes().size()) {
                throw new IllegalArgumentException("Duplicate required code");
            }
            request.requiredCodes().forEach(this::activeCode);
            if (request.minimumAge() != null) {
                if (request.dateOfBirthCode() == null) throw new IllegalArgumentException("Age rule requires dateOfBirthCode");
                var dob = activeCode(request.dateOfBirthCode());
                if (!"DATE".equals(dob.getFieldType()) || request.requiresVerifiedDateOfBirth()
                        && "NONE".equals(dob.getVerificationMode())) {
                    throw new IllegalArgumentException("Age rule requires a DATE field with review when verification is requested");
                }
            } else if (request.dateOfBirthCode() != null || request.requiresVerifiedDateOfBirth()) {
                throw new IllegalArgumentException("Date of birth settings require minimumAge");
            }
        }
        var rule = rules.findByTargetKey(targetKey(target)).orElseGet(CustomerEligibilityRule::new);
        rule.setTargetKey(targetKey(target)); rule.setScope(target.scope());
        rule.setServiceTypeId(target.serviceTypeId()); rule.setOptionId(target.optionId());
        rule.setEnabled(request.enabled()); rule.setMinimumAge(request.minimumAge());
        rule.setDateOfBirthCode(request.dateOfBirthCode());
        rule.setRequiresVerifiedDateOfBirth(request.requiresVerifiedDateOfBirth());
        rule.setRequiredCodesJson(json(request.requiredCodes()));
        return ruleResponse(rules.save(rule));
    }

    public List<Rule> listRules() { return rules.findAll().stream().map(this::ruleResponse).toList(); }

    public Eligibility eligibility(Long userId, Long serviceId, Long optionId) {
        validateContext(serviceId, optionId);
        var answers = answers(customer(userId).getCustomerId());
        var all = activeDefinitions();
        List<Reason> reasons = new ArrayList<>();
        for (var r : applicable(serviceId, optionId)) {
            if (!r.isBlocksBooking()) continue;
            String status = effectiveStatus(answers.get(r.getRequirementId()), r, answers, all);
            if (!Set.of("PROVIDED", "APPROVED").contains(status)) {
                reasons.add(new Reason(status, r.getCode(), r.getLabel() + ": " + status));
            }
        }
        for (var rule : applicableRules(serviceId, optionId)) {
            for (String code : strings(rule.getRequiredCodesJson())) {
                var required = all.get(code);
                String status = required == null ? "MISSING" : effectiveStatus(
                        answers.get(required.getRequirementId()), required, answers, all);
                if (!Set.of("PROVIDED", "APPROVED").contains(status)) {
                    var reason = new Reason(status, code, code + ": " + status);
                    if (!reasons.contains(reason)) reasons.add(reason);
                }
            }
            if (rule.getMinimumAge() == null) continue;
            var dob = all.get(rule.getDateOfBirthCode());
            var answer = dob == null ? null : answers.get(dob.getRequirementId());
            String status = dob == null ? "MISSING" : effectiveStatus(answer, dob, answers, all);
            if (!Set.of("PROVIDED", "APPROVED").contains(status)
                    || rule.isRequiresVerifiedDateOfBirth() && !"APPROVED".equals(status)) {
                reasons.add(new Reason("DATE_OF_BIRTH_UNVERIFIED", rule.getDateOfBirthCode(),
                        "Provide a current" + (rule.isRequiresVerifiedDateOfBirth() ? " approved" : "") + " date of birth"));
                continue;
            }
            LocalDate date;
            try { date = LocalDate.parse(read(answer.getValueJson()).asText()); }
            catch (RuntimeException exception) {
                reasons.add(new Reason("INVALID_DATE_OF_BIRTH", rule.getDateOfBirthCode(), "Resubmit date of birth"));
                continue;
            }
            if (date.isAfter(today()) || Period.between(date, today()).getYears() < rule.getMinimumAge()) {
                reasons.add(new Reason("MINIMUM_AGE", rule.getDateOfBirthCode(), "Minimum age is " + rule.getMinimumAge()));
            }
        }
        return new Eligibility(reasons.isEmpty(), reasons);
    }

    public void requireEligible(Long userId, Long serviceId, Long optionId) {
        var result = eligibility(userId, serviceId, optionId);
        if (!result.eligible()) throw new CustomerEligibilityException(result);
    }

    private void validateValue(Long userId, CustomerRequirement r, JsonNode value) {
        if (value == null || value.isNull()) throw new IllegalArgumentException("Value is required: " + r.getCode());
        String type = r.getFieldType();
        if (upload(type)) { validateFiles(userId, r, value); return; }
        boolean valid = switch (type) {
            case "NUMBER" -> value.isNumber();
            case "BOOLEAN" -> value.isBoolean();
            case "MULTI_SELECT" -> value.isArray() && !value.isEmpty();
            default -> value.isTextual() && !value.asText().isBlank() && value.asText().length() <= 4000;
        };
        if (!valid) throw new IllegalArgumentException("Invalid value type: " + r.getCode());
        try {
            if ("DATE".equals(type)) LocalDate.parse(value.asText());
            if ("TIME".equals(type)) LocalTime.parse(value.asText());
        } catch (java.time.format.DateTimeParseException e) { throw new IllegalArgumentException("Invalid date/time: " + r.getCode()); }
        if ("EMAIL".equals(type) && !value.asText().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Invalid email");
        }
        if ("PHONE".equals(type) && !value.asText().matches("\\+?[0-9 ()-]{7,30}")) throw new IllegalArgumentException("Invalid phone");
        var choices = strings(r.getChoicesJson());
        if ("SELECT".equals(type) && !choices.contains(value.asText())) throw new IllegalArgumentException("Invalid selection");
        if ("MULTI_SELECT".equals(type)) {
            Set<String> seen = new HashSet<>();
            value.forEach(v -> {
                if (!v.isTextual() || !choices.contains(v.asText()) || !seen.add(v.asText())) {
                    throw new IllegalArgumentException("Invalid or duplicate selection");
                }
            });
        }
    }

    private void validateFiles(Long userId, CustomerRequirement r, JsonNode value) {
        JsonNode entries = value.isObject() ? value.get("files") : null;
        if (entries == null || !entries.isArray() || entries.size() < (r.getMinimumFiles() == null ? 1 : r.getMinimumFiles())
                || entries.size() > (r.getMaximumFiles() == null ? 1 : r.getMaximumFiles())) {
            throw new IllegalArgumentException("Invalid files array: " + r.getCode());
        }
        Set<String> keys = new HashSet<>();
        for (JsonNode entry : entries) {
            if (!entry.isObject() || !entry.path("key").isTextual()) throw new IllegalArgumentException("Each file requires a key");
            String key = entry.get("key").asText();
            if (!keys.add(key)) throw new IllegalArgumentException("Duplicate file key");
            var metadata = files.inspectCustomerFile(userId, key);
            String fileType = FILE_TYPES.get(metadata.contentType());
            var allowed = strings(r.getAllowedFileTypesJson());
            if (fileType == null || "IMAGE_UPLOAD".equals(r.getFieldType()) && "PDF".equals(fileType)
                    || !allowed.isEmpty() && !allowed.contains(fileType)
                    || metadata.size() > (r.getMaximumFileSizeMb() == null ? 100L : r.getMaximumFileSizeMb()) * 1024 * 1024) {
                throw new IllegalArgumentException("File type or size is not allowed: " + r.getCode());
            }
        }
    }

    private String effectiveStatus(CustomerRequirementSubmission s, CustomerRequirement r,
                                   Map<Long, CustomerRequirementSubmission> answers, Map<String, CustomerRequirement> all) {
        if (s == null || !"ACTIVE".equals(r.getStatus())
                || !Objects.equals(s.getDefinitionVersion(), r.getDefinitionVersion())) return "MISSING";
        if (expired(s)) return "EXPIRED";
        if ("APPROVED".equals(s.getStatus())) {
            Map<String, Integer> versions = parse(s.getEvidenceVersionsJson(), new TypeReference<>() {});
            for (String code : strings(r.getEvidenceCodesJson())) {
                var evidence = all.get(code);
                var answer = evidence == null ? null : answers.get(evidence.getRequirementId());
                if (answer == null || !"APPROVED".equals(answer.getStatus()) || expired(answer)
                        || !Objects.equals(answer.getDefinitionVersion(), evidence.getDefinitionVersion())
                        || !Objects.equals(versions.get(code), answer.getAnswerVersion())) return "PENDING";
            }
        }
        return s.getStatus();
    }

    private List<CustomerRequirement> applicable(Long serviceId, Long optionId) {
        var all = activeDefinitions();
        Map<String, CustomerRequirement> result = new LinkedHashMap<>();
        all.values().stream().filter(r -> targets(r).stream().anyMatch(t -> matches(t, serviceId, optionId)))
                .forEach(r -> result.put(r.getCode(), r));
        for (var rule : applicableRules(serviceId, optionId)) {
            if (all.containsKey(rule.getDateOfBirthCode())) result.put(rule.getDateOfBirthCode(), all.get(rule.getDateOfBirthCode()));
            for (String code : strings(rule.getRequiredCodesJson())) {
                if (all.containsKey(code)) result.put(code, all.get(code));
            }
        }
        for (var r : new ArrayList<>(result.values())) {
            for (String code : strings(r.getEvidenceCodesJson())) {
                if (all.containsKey(code)) result.put(code, all.get(code));
            }
        }
        return new ArrayList<>(result.values());
    }

    private List<CustomerEligibilityRule> applicableRules(Long serviceId, Long optionId) {
        return rules.findAllByEnabledTrue().stream().filter(r -> matches(
                new Target(r.getScope(), r.getServiceTypeId(), r.getOptionId()), serviceId, optionId)).toList();
    }
    private boolean matches(Target t, Long serviceId, Long optionId) {
        return "GLOBAL".equals(t.scope()) || Objects.equals(t.serviceTypeId(), serviceId) && serviceId != null
                && ("SERVICE".equals(t.scope()) || optionId != null && Objects.equals(t.optionId(), optionId));
    }
    private void validateTarget(Target t) {
        switch (t.scope()) {
            case "GLOBAL" -> { if (t.serviceTypeId() != null || t.optionId() != null) throw new IllegalArgumentException("GLOBAL cannot specify service/option IDs"); }
            case "SERVICE" -> { if (t.serviceTypeId() == null || t.optionId() != null) throw new IllegalArgumentException("SERVICE requires only serviceTypeId"); }
            case "OPTION" -> { if (t.serviceTypeId() == null || t.optionId() == null) throw new IllegalArgumentException("OPTION requires serviceTypeId and optionId"); }
            default -> throw new IllegalArgumentException("Scope must be GLOBAL, SERVICE or OPTION");
        }
        validateContext(t.serviceTypeId(), t.optionId());
    }
    private void validateContext(Long serviceId, Long optionId) {
        if (optionId != null && serviceId == null) throw new IllegalArgumentException("optionId requires serviceTypeId");
        if (serviceId != null && services.findById(serviceId).filter(s -> "ACTIVE".equals(s.getStatus())).isEmpty()) {
            throw new ResourceNotFoundException("Active service not found");
        }
        if (optionId != null && options.findByOptionIdAndServiceProviderTypeId(optionId, serviceId)
                .filter(o -> o.activeForService(serviceId)).isEmpty()) throw new ResourceNotFoundException("Active option not found for service");
    }
    private String targetKey(Target t) { return t.scope() + ":" + t.serviceTypeId() + ":" + t.optionId(); }
    private Customer customer(Long userId) {
        return customers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Complete the customer profile first"));
    }
    private CustomerRequirement requirement(Long id) {
        return requirements.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer requirement not found"));
    }
    private CustomerRequirement activeCode(String code) {
        return requirements.findByCode(code).filter(r -> "ACTIVE".equals(r.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Active requirement code not found: " + code));
    }
    private Map<String, CustomerRequirement> activeDefinitions() {
        Map<String, CustomerRequirement> all = new LinkedHashMap<>();
        requirements.findAllByStatusOrderByDisplayOrderAscRequirementIdAsc("ACTIVE").forEach(r -> all.put(r.getCode(), r));
        return all;
    }
    private Map<Long, CustomerRequirementSubmission> answers(Long customerId) {
        Map<Long, CustomerRequirementSubmission> result = new HashMap<>();
        submissions.findAllByCustomerId(customerId).forEach(s -> result.put(s.getRequirementId(), s));
        return result;
    }
    private void invalidateDependents(Long customerId, String evidenceCode) {
        Set<Long> dependentIds = new HashSet<>();
        requirements.findAll().stream().filter(r -> strings(r.getEvidenceCodesJson()).contains(evidenceCode))
                .forEach(r -> dependentIds.add(r.getRequirementId()));
        for (var answer : submissions.findAllByCustomerId(customerId)) {
            if (dependentIds.contains(answer.getRequirementId()) && "APPROVED".equals(answer.getStatus())) {
                answer.setStatus("PENDING"); answer.setReviewedAt(null); answer.setReviewedBy(null);
                answer.setEvidenceVersionsJson("{}");
                submissions.save(answer);
            }
        }
    }
    private Requirement response(CustomerRequirement r) {
        return new Requirement(r.getRequirementId(), r.getDefinitionVersion(), r.getStatus(),
                new Definition(r.getCode(), r.getLabel(), r.getDescription(), r.getFieldType(), r.isRequired(),
                        r.getVerificationMode(), r.isBlocksBooking(), targets(r), strings(r.getEvidenceCodesJson()),
                        strings(r.getChoicesJson()), r.getMinimumFiles(), r.getMaximumFiles(), r.getMaximumFileSizeMb(),
                        strings(r.getAllowedFileTypesJson()), r.getDisplayOrder()));
    }
    private Submission submissionResponse(CustomerRequirementSubmission s, CustomerRequirement r,
            Map<Long, CustomerRequirementSubmission> answers, Map<String, CustomerRequirement> all) {
        if (s == null) return null;
        return new Submission(s.getSubmissionId(), s.getCustomerId(), r.getRequirementId(), r.getCode(), read(s.getValueJson()),
                effectiveStatus(s, r, answers, all), s.getStatus(), s.getExpiresOn(), s.getReviewedBy(), s.getReviewedAt(), s.getRejectionReason(),
                s.getAnswerVersion(), s.getDefinitionVersion());
    }
    private Rule ruleResponse(CustomerEligibilityRule r) {
        return new Rule(r.getRuleId(), new Target(r.getScope(), r.getServiceTypeId(), r.getOptionId()),
                new AgeRule(r.isEnabled(), r.getMinimumAge(), r.getDateOfBirthCode(), r.isRequiresVerifiedDateOfBirth(),
                        strings(r.getRequiredCodesJson())));
    }
    private boolean upload(String type) { return type.endsWith("_UPLOAD"); }
    private boolean expired(CustomerRequirementSubmission s) { return s.getExpiresOn() != null && s.getExpiresOn().isBefore(today()); }
    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Colombo")); }
    private List<Target> targets(CustomerRequirement r) { return parse(r.getTargetsJson(), new TypeReference<>() {}); }
    private List<String> strings(String value) { return value == null ? List.of() : parse(value, new TypeReference<>() {}); }
    private <T> T parse(String value, TypeReference<T> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception e) { throw new IllegalStateException("Invalid stored customer requirement configuration", e); }
    }
    private JsonNode read(String value) {
        try { return mapper.readTree(value); }
        catch (Exception e) { throw new IllegalStateException("Invalid stored customer answer", e); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid customer requirement JSON", e); }
    }
}
