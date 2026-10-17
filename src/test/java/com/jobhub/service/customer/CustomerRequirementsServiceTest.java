package com.jobhub.service.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.customer.CustomerRequirementsApi.*;
import com.jobhub.entity.customer.*;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.*;
import com.jobhub.repository.customer.*;
import com.jobhub.repository.provider.*;
import com.jobhub.service.upload.S3PrivateDocumentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class CustomerRequirementsServiceTest {
    @Mock CustomerRequirementRepository requirements;
    @Mock CustomerRequirementSubmissionRepository submissions;
    @Mock CustomerEligibilityRuleRepository rules;
    @Mock CustomerRepository customers;
    @Mock ServiceProviderTypeRepository services;
    @Mock ServiceOptionRepository options;
    @Mock S3PrivateDocumentService files;
    CustomerRequirementsService service;
    ObjectMapper mapper = new ObjectMapper();
    List<CustomerRequirement> definitions = new ArrayList<>();
    List<CustomerRequirementSubmission> answers = new ArrayList<>();
    List<CustomerEligibilityRule> ageRules = new ArrayList<>();

    @BeforeEach
    void setup() {
        service = new CustomerRequirementsService(requirements, submissions, rules, customers, services, options, files, mapper);
        var customer = new Customer(); customer.setCustomerId(7L); customer.setUserId(25L);
        lenient().when(customers.findByUserId(25L)).thenReturn(Optional.of(customer));
        lenient().when(requirements.findAll()).thenAnswer(i -> definitions);
        lenient().when(requirements.findAllByStatusOrderByDisplayOrderAscRequirementIdAsc("ACTIVE"))
                .thenAnswer(i -> definitions.stream().filter(r -> "ACTIVE".equals(r.getStatus())).toList());
        lenient().when(requirements.findById(anyLong())).thenAnswer(i -> definitions.stream()
                .filter(r -> r.getRequirementId().equals(i.getArgument(0))).findFirst());
        lenient().when(requirements.findByCode(anyString())).thenAnswer(i -> definitions.stream()
                .filter(r -> r.getCode().equals(i.getArgument(0))).findFirst());
        lenient().when(submissions.findAllByCustomerId(7L)).thenAnswer(i -> answers);
        lenient().when(submissions.findById(anyLong())).thenAnswer(i -> answers.stream()
                .filter(s -> s.getSubmissionId().equals(i.getArgument(0))).findFirst());
        lenient().when(submissions.save(any())).thenAnswer(i -> {
            CustomerRequirementSubmission s = i.getArgument(0);
            if (s.getSubmissionId() == null) { s.setSubmissionId((long) answers.size() + 100); answers.add(s); }
            return s;
        });
        lenient().when(rules.findAllByEnabledTrue()).thenAnswer(i -> ageRules);
        var type = new ServiceProviderType(); type.setStatus("ACTIVE");
        lenient().when(services.findById(3L)).thenReturn(Optional.of(type));
        var option = new ServiceOption(); option.setStatus("ACTIVE");
        lenient().when(options.findByOptionIdAndServiceProviderTypeId(12L, 3L)).thenReturn(Optional.of(option));
    }

    private CustomerRequirement field(long id, String code, String type, String mode, boolean blocks, Target... targets) throws Exception {
        var r = new CustomerRequirement(); r.setRequirementId(id); r.setCode(code); r.setLabel(code);
        r.setFieldType(type); r.setVerificationMode(mode); r.setRequired(true); r.setBlocksBooking(blocks);
        r.setStatus("ACTIVE"); r.setDefinitionVersion(1); r.setDisplayOrder(0);
        r.setTargetsJson(mapper.writeValueAsString(targets)); r.setEvidenceCodesJson("[]");
        r.setChoicesJson("[]"); r.setAllowedFileTypesJson("[]"); definitions.add(r); return r;
    }
    private CustomerRequirementSubmission answer(CustomerRequirement r, String value, String status) {
        var s = new CustomerRequirementSubmission(); s.setSubmissionId(r.getRequirementId() + 100);
        s.setCustomerId(7L); s.setRequirementId(r.getRequirementId()); s.setValueJson(value);
        s.setDefinitionVersion(1); s.setAnswerVersion(1); s.setStatus(status); s.setEvidenceVersionsJson("{}");
        answers.add(s); return s;
    }
    private Target global() { return new Target("GLOBAL", null, null); }
    private CustomerEligibilityRule age(String scope, int minimum, boolean verified) {
        var r = new CustomerEligibilityRule(); r.setScope(scope); r.setEnabled(true); r.setMinimumAge(minimum);
        r.setDateOfBirthCode("DOB"); r.setRequiresVerifiedDateOfBirth(verified);
        if (!scope.equals("GLOBAL")) r.setServiceTypeId(3L);
        if (scope.equals("OPTION")) r.setOptionId(12L);
        ageRules.add(r); return r;
    }

    @Test void combinesGlobalServiceAndOptionFormsWithoutDuplicates() throws Exception {
        field(1, "NIC", "DOCUMENT_UPLOAD", "DOCUMENT_REVIEW", true, global());
        field(2, "SERVICE_NOTES", "TEXT", "NONE", false, new Target("SERVICE", 3L, null));
        field(3, "OPTION_NOTES", "TEXT", "NONE", false, new Target("OPTION", 3L, 12L));
        field(4, "OTHER", "TEXT", "NONE", false, new Target("OPTION", 3L, 13L));
        assertThat(service.form(25L, 3L, 12L)).extracting(i -> i.requirement().definition().code())
                .containsExactly("NIC", "SERVICE_NOTES", "OPTION_NOTES");
        assertThat(service.form(25L, null, null)).hasSize(1);
    }

    @Test void collectOnlyFieldsNeverBlockBooking() throws Exception {
        field(1, "GENDER", "SELECT", "NONE", false, global());
        assertThat(service.eligibility(25L, 3L, 12L).eligible()).isTrue();
    }

    @Test void blockingRequirementsNeedCurrentUnexpiredAnswers() throws Exception {
        var r = field(1, "NIC", "DOCUMENT_UPLOAD", "DOCUMENT_REVIEW", true, global());
        assertThat(service.eligibility(25L, 3L, 12L).reasons()).extracting(Reason::code).contains("MISSING");
        var s = answer(r, "{}", "PENDING");
        assertThat(service.eligibility(25L, 3L, 12L).eligible()).isFalse();
        s.setStatus("APPROVED");
        assertThat(service.eligibility(25L, 3L, 12L).eligible()).isTrue();
        s.setExpiresOn(LocalDate.now(ZoneId.of("Asia/Colombo")).minusDays(1));
        assertThat(service.eligibility(25L, 3L, 12L).reasons()).extracting(Reason::code).contains("EXPIRED");
        s.setExpiresOn(null); r.setDefinitionVersion(2);
        assertThat(service.eligibility(25L, 3L, 12L).reasons()).extracting(Reason::code).contains("MISSING");
    }

    @Test void noneReviewSubmissionIsProvidedAndUntouchedAnswersRemain() throws Exception {
        field(1, "NOTES", "TEXT", "NONE", true, global());
        var second = field(2, "OTHER", "TEXT", "NONE", false, global());
        var untouched = answer(second, "\"existing\"", "PROVIDED");
        var result = service.submit(25L, new Submit(List.of(new Entry(1L, mapper.readTree("\"hello\""), null))));
        assertThat(result.get(0).status()).isEqualTo("PROVIDED");
        assertThat(untouched.getValueJson()).isEqualTo("\"existing\"");
        assertThat(service.eligibility(25L, 3L, 12L).eligible()).isTrue();
    }

    @Test void validatesWholeBatchBeforeSavingAnything() throws Exception {
        field(1, "NOTES", "TEXT", "NONE", false, global());
        field(2, "COUNT", "NUMBER", "NONE", false, global());
        var request = new Submit(List.of(new Entry(1L, mapper.readTree("\"valid\""), null),
                new Entry(2L, mapper.readTree("\"invalid\""), null)));
        assertThatThrownBy(() -> service.submit(25L, request)).isInstanceOf(IllegalArgumentException.class);
        verify(submissions, never()).save(any());
    }

    @Test void privateFileOwnershipAndActualMetadataAreChecked() throws Exception {
        var r = field(1, "NIC", "IMAGE_UPLOAD", "DOCUMENT_REVIEW", true, global());
        r.setMaximumFileSizeMb(1);
        when(files.inspectCustomerFile(25L, "customers/25/documents/nic.jpg"))
                .thenReturn(new S3PrivateDocumentService.CustomerFile("image/jpeg", 2000000));
        var request = new Submit(List.of(new Entry(1L, mapper.readTree(
                "{\"files\":[{\"key\":\"customers/25/documents/nic.jpg\"}]}"), null)));
        assertThatThrownBy(() -> service.submit(25L, request)).hasMessageContaining("File type or size");
        verify(submissions, never()).save(any());
    }

    @Test void customerCannotReviewOrForgeApprovalThroughSubmission() throws Exception {
        var r = field(1, "NIC", "DOCUMENT_UPLOAD", "DOCUMENT_REVIEW", true, global());
        var s = answer(r, "{}", "APPROVED"); s.setReviewedBy(99L);
        when(files.inspectCustomerFile(25L, "customers/25/documents/nic.pdf"))
                .thenReturn(new S3PrivateDocumentService.CustomerFile("application/pdf", 100));
        var result = service.submit(25L, new Submit(List.of(new Entry(1L, mapper.readTree(
                "{\"files\":[{\"key\":\"customers/25/documents/nic.pdf\"}]}"), null))));
        assertThat(result.get(0).status()).isEqualTo("PENDING");
        assertThat(result.get(0).reviewedBy()).isNull();
        assertThat(result.get(0).answerVersion()).isEqualTo(2);
    }

    @Test void reviewedDetailRequiresCurrentApprovedEvidenceAndStaleReviewIsRejected() throws Exception {
        var nic = field(1, "NIC", "DOCUMENT_UPLOAD", "DOCUMENT_REVIEW", false, global());
        var dob = field(2, "DOB", "DATE", "DOCUMENT_REVIEW", true, global());
        dob.setEvidenceCodesJson("[\"NIC\"]");
        var n = answer(nic, "{}", "PENDING");
        var d = answer(dob, "\"2000-01-01\"", "PENDING");
        assertThatThrownBy(() -> service.review(99L, d.getSubmissionId(), new Review("APPROVED", null, 1)))
                .hasMessageContaining("supporting evidence");
        n.setStatus("APPROVED");
        assertThat(service.review(99L, d.getSubmissionId(), new Review("APPROVED", null, 1)).status()).isEqualTo("APPROVED");
        n.setAnswerVersion(2);
        assertThat(service.eligibility(25L, 3L, 12L).reasons()).extracting(Reason::code).contains("PENDING");
        assertThatThrownBy(() -> service.review(99L, d.getSubmissionId(), new Review("APPROVED", null, 99)))
                .isInstanceOf(ConflictException.class);
    }

    @Test void replacingEvidenceInvalidatesRelatedApprovalsAndReturnsThemToQueue() throws Exception {
        var nic = field(1, "NIC", "DOCUMENT_UPLOAD", "DOCUMENT_REVIEW", false, global());
        var dob = field(2, "DOB", "DATE", "DOCUMENT_REVIEW", true, global());
        dob.setEvidenceCodesJson("[\"NIC\"]");
        answer(nic, "{}", "APPROVED");
        var d = answer(dob, "\"2000-01-01\"", "APPROVED"); d.setEvidenceVersionsJson("{\"NIC\":1}");
        when(files.inspectCustomerFile(25L, "customers/25/documents/new.pdf"))
                .thenReturn(new S3PrivateDocumentService.CustomerFile("application/pdf", 100));
        service.submit(25L, new Submit(List.of(new Entry(1L, mapper.readTree(
                "{\"files\":[{\"key\":\"customers/25/documents/new.pdf\"}]}"), null))));
        assertThat(d.getStatus()).isEqualTo("PENDING");
    }

    @Test void exactBirthdayPassesButOneDayYoungerFails() throws Exception {
        var dob = field(1, "DOB", "DATE", "NONE", false, global());
        LocalDate boundary = LocalDate.now(ZoneId.of("Asia/Colombo")).minusYears(18);
        var s = answer(dob, "\"" + boundary + "\"", "PROVIDED");
        age("SERVICE", 18, false);
        assertThat(service.eligibility(25L, 3L, 12L).eligible()).isTrue();
        s.setValueJson("\"" + boundary.plusDays(1) + "\"");
        assertThat(service.eligibility(25L, 3L, 12L).reasons()).extracting(Reason::code).contains("MINIMUM_AGE");
    }

    @Test void stricterOptionRuleAppliesAndVerifiedAgeCannotUseUnreviewedDob() throws Exception {
        var dob = field(1, "DOB", "DATE", "ADMIN_REVIEW", false, global());
        var s = answer(dob, "\"" + LocalDate.now(ZoneId.of("Asia/Colombo")).minusYears(20) + "\"", "APPROVED");
        age("GLOBAL", 18, true); age("OPTION", 21, true);
        assertThat(service.eligibility(25L, 3L, 12L).reasons()).extracting(Reason::code).containsExactly("MINIMUM_AGE");
        assertThat(service.eligibility(25L, 3L, null).eligible()).isTrue();
        s.setStatus("PENDING");
        assertThat(service.eligibility(25L, 3L, null).reasons()).extracting(Reason::code).contains("DATE_OF_BIRTH_UNVERIFIED");
    }

    @Test void rejectsOptionWithoutServiceAndUnrelatedOption() {
        assertThatThrownBy(() -> service.form(25L, null, 12L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.form(25L, 3L, 99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void bookingGuardReturnsStructuredFailure() throws Exception {
        field(1, "NIC", "DOCUMENT_UPLOAD", "DOCUMENT_REVIEW", true, global());
        assertThatThrownBy(() -> service.requireEligible(25L, 3L, 12L))
                .isInstanceOfSatisfying(CustomerEligibilityException.class,
                        e -> assertThat(e.getEligibility().reasons().get(0).requirementCode()).isEqualTo("NIC"));
    }

    @Test void globallyCollectedNicCanBlockOnlyOneOption() throws Exception {
        var nic = field(1, "NIC", "DOCUMENT_UPLOAD", "DOCUMENT_REVIEW", false, global());
        var rule = new CustomerEligibilityRule(); rule.setScope("OPTION"); rule.setServiceTypeId(3L);
        rule.setOptionId(12L); rule.setEnabled(true); rule.setRequiredCodesJson("[\"NIC\"]"); ageRules.add(rule);
        assertThat(service.eligibility(25L, null, null).eligible()).isTrue();
        assertThat(service.eligibility(25L, 3L, null).eligible()).isTrue();
        assertThat(service.eligibility(25L, 3L, 12L).eligible()).isFalse();
        answer(nic, "{}", "APPROVED");
        assertThat(service.eligibility(25L, 3L, 12L).eligible()).isTrue();
    }

    @Test void configuresDocumentOnlyRuleWithoutAnAgeRestriction() throws Exception {
        field(1, "NIC", "DOCUMENT_UPLOAD", "DOCUMENT_REVIEW", false, global());
        when(rules.findByTargetKey(anyString())).thenReturn(Optional.empty());
        when(rules.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.configureRule(new Target("SERVICE", 3L, null),
                new AgeRule(true, null, null, false, List.of("NIC")));
        assertThat(result.configuration().requiredCodes()).containsExactly("NIC");
        assertThat(result.configuration().minimumAge()).isNull();
    }

    private Definition definition(String code, String type, String mode, List<Target> targets, List<String> evidence) {
        return new Definition(code, code, null, type, true, mode, false, targets, evidence,
                List.of(), null, null, null, List.of(), 0);
    }

    @Test void configuresOneReusableDefinitionForSeveralServiceTargets() {
        var type = new ServiceProviderType(); type.setStatus("ACTIVE");
        when(services.findById(4L)).thenReturn(Optional.of(type));
        when(requirements.save(any())).thenAnswer(i -> {
            CustomerRequirement r = i.getArgument(0); r.setRequirementId(1L); return r;
        });
        var result = service.create(definition("NIC", "DOCUMENT_UPLOAD", "DOCUMENT_REVIEW",
                List.of(new Target("SERVICE", 3L, null), new Target("SERVICE", 4L, null)), List.of()));
        assertThat(result.definition().targets()).hasSize(2);
        assertThat(result.version()).isEqualTo(1);
    }

    @Test void invalidTargetOrDuplicateCodeCannotBeSaved() throws Exception {
        field(1, "NIC", "DOCUMENT_UPLOAD", "DOCUMENT_REVIEW", false, global());
        assertThatThrownBy(() -> service.create(definition("NIC", "TEXT", "NONE", List.of(global()), List.of())))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.create(definition("OTHER", "TEXT", "NONE",
                List.of(new Target("GLOBAL", 3L, null)), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(requirements, never()).save(any());
    }

    @Test void evidenceDependencyMustReferenceReviewedUpload() throws Exception {
        field(1, "NOTES", "TEXT", "NONE", false, global());
        assertThatThrownBy(() -> service.create(definition("DOB", "DATE", "DOCUMENT_REVIEW",
                List.of(global()), List.of("NOTES"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Evidence must reference");
    }
}
