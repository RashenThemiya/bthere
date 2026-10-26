package com.jobhub.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.provider.ServiceCustomFieldRequest;
import com.jobhub.dto.provider.OptionDynamicFieldRequest;
import com.jobhub.entity.provider.*;
import com.jobhub.repository.provider.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceCustomFieldServiceTest {
    @Test void choiceDefaultsRoundTripThroughSharedScopingAndCanBeChangedOrCleared() throws Exception {
        var mapper = new ObjectMapper();
        var realService = new ServiceCustomFieldService(mapper, typeRepository, optionRepository,
                fieldRepository, submissionRepository, providerOptionRepository, assignmentRepository,
                profileService, approvalService, serviceSetupService, profileFields);
        var definition = mapper.readValue("""
                {"scope":"PROVIDER_REQUIREMENT","code":"VEHICLE","label":"Vehicle",
                 "fieldType":"SELECT","options":["CAR","BIKE"],"defaultValue":"CAR"}
                """, ServiceCustomFieldRequest.class).withOptionIds(List.of(12L));
        var saved = new java.util.concurrent.atomic.AtomicReference<ServiceCustomField>();
        when(fieldRepository.save(any())).thenAnswer(i -> {
            ServiceCustomField field = i.getArgument(0); field.setFieldId(25L); saved.set(field); return field;
        });
        var created = realService.create(1L, definition);
        assertThat(created.defaultValue().asText()).isEqualTo("CAR");
        assertThat(saved.get().getDefaultValueJson()).isEqualTo("\"CAR\"");
        assertThat(mapper.valueToTree(created).path("defaultValue").asText()).isEqualTo("CAR");
        when(fieldRepository.findById(25L)).thenAnswer(i -> Optional.of(saved.get()));
        var changedJson = mapper.valueToTree(definition);
        ((com.fasterxml.jackson.databind.node.ObjectNode) changedJson).put("fieldType", "MULTI_SELECT")
                .set("defaultValue", mapper.readTree("[\"CAR\",\"BIKE\"]"));
        var changed = realService.update(1L, 25L, mapper.treeToValue(changedJson, ServiceCustomFieldRequest.class));
        assertThat(changed.defaultValue()).isEqualTo(mapper.readTree("[\"CAR\",\"BIKE\"]"));
        // Removing a choice without updating its default must fail before persistence.
        ((com.fasterxml.jackson.databind.node.ObjectNode) changedJson).set("options", mapper.readTree("[\"BIKE\"]"));
        var invalid = mapper.treeToValue(changedJson, ServiceCustomFieldRequest.class);
        assertThatThrownBy(() -> realService.update(1L, 25L, invalid)).hasMessageContaining("configured choice");
        ((com.fasterxml.jackson.databind.node.ObjectNode) changedJson).remove("defaultValue");
        assertThat(realService.update(1L, 25L, mapper.treeToValue(changedJson, ServiceCustomFieldRequest.class))
                .defaultValue()).isNull();
        assertThat(saved.get().getDefaultValueJson()).isNull();
        verify(submissionRepository, never()).save(any());
        verify(submissionRepository, atLeastOnce()).saveAll(List.of());
    }
    @Test void linkedContextMutatesTheSameFieldAndUnrelatedContextCannot() throws Exception {
        var field = new ServiceCustomField();
        field.setFieldId(5L); field.setServiceProviderTypeId(2L);
        field.setOptionIds(Set.of(12L)); field.setVersion(1); field.setStatus("ACTIVE");
        when(fieldRepository.findById(5L)).thenReturn(Optional.of(field));
        when(fieldRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var updated = service.update(1L, 5L, request("\"optionIds\":[12]"));
        assertThat(updated.fieldId()).isEqualTo(5L);
        assertThat(updated.ownerServiceTypeId()).isEqualTo(2L);
        assertThat(updated.configurationScope()).isEqualTo("SHARED_OPTION");
        var inactive = service.updateStatus(1L, 5L, "INACTIVE");
        assertThat(inactive.status()).isEqualTo("INACTIVE");
        assertThat(field.getStatus()).isEqualTo("INACTIVE");
        assertThatThrownBy(() -> service.updateStatus(99L, 5L, "ACTIVE"))
                .isInstanceOf(com.jobhub.exception.ResourceNotFoundException.class);
        assertThat(field.getStatus()).isEqualTo("INACTIVE");
    }

    @Test void sharedFieldJsonExposesHistoricalOwnerWithoutChangingNavigationContext() throws Exception {
        var field = new ServiceCustomField();
        field.setFieldId(5L); field.setServiceProviderTypeId(2L);
        field.setScope("PROVIDER_REQUIREMENT"); field.setStatus("ACTIVE"); field.setOptionIds(Set.of(12L));
        var option = optionRepository.findByOptionIdAndServiceProviderTypeId(12L, 1L).orElseThrow();
        when(optionRepository.findAllByServiceProviderTypeIdOrderByDisplayOrderAscNameAsc(1L))
                .thenReturn(List.of(option));
        when(fieldRepository.findSharedForOption(12L)).thenReturn(List.of(field));
        var fields = service.listAdmin(1L);
        assertThat(fields).hasSize(1);
        var json = new ObjectMapper().valueToTree(fields.get(0));
        assertThat(json.path("serviceTypeId").asLong()).isEqualTo(2L);
        assertThat(json.path("ownerServiceTypeId").asLong()).isEqualTo(2L);
        assertThat(json.path("configurationScope").asText()).isEqualTo("SHARED_OPTION");
        assertThat(json.path("optionIds").get(0).asLong()).isEqualTo(12L);
    }

    @Mock ObjectMapper objectMapper;
    @Mock ServiceProviderTypeRepository typeRepository;
    @Mock ServiceOptionRepository optionRepository;
    @Mock ServiceCustomFieldRepository fieldRepository;
    @Mock ProviderCustomFieldSubmissionRepository submissionRepository;
    @Mock ProviderServiceOptionRepository providerOptionRepository;
    @Mock ServiceProviderTypeAssignmentRepository assignmentRepository;
    @Mock ProviderProfileService profileService;
    @Mock ProviderServiceApprovalService approvalService;
    @Mock ServiceSetupService serviceSetupService;
    @Mock ProviderProfileFields profileFields;
    @InjectMocks ServiceCustomFieldService service;

    @Test void profileBackedFieldsIgnoreOldAnswersAndRejectDuplicateInput() throws Exception {
        var mapper = new ObjectMapper();
        var realService = new ServiceCustomFieldService(mapper, typeRepository, optionRepository,
                fieldRepository, submissionRepository, providerOptionRepository, assignmentRepository,
                profileService, approvalService, serviceSetupService, profileFields);
        var definition = mapper.readValue("""
                {"scope":"PROVIDER_REQUIREMENT","code":"GENDER","label":"Gender","fieldType":"SELECT",
                 "valueSource":"PROVIDER_PROFILE","profileField":"gender","options":["MALE","FEMALE"],
                 "customerVisible":true,"searchable":true}
                """, ServiceCustomFieldRequest.class);
        when(fieldRepository.save(any())).thenAnswer(i -> {
            ServiceCustomField item = i.getArgument(0); item.setFieldId(25L); return item;
        });
        var created = realService.create(1L, definition);
        assertThat(created.valueSource()).isEqualTo("PROVIDER_PROFILE");
        assertThat(created.profileField()).isEqualTo("gender");
        assertThat(definition.withOptionIds(List.of(12L)).profileField()).isEqualTo("gender");
        var field = new ServiceCustomField(); field.setFieldId(25L); field.setScope("PROVIDER_REQUIREMENT");
        field.setStatus("ACTIVE"); field.setServiceProviderTypeId(1L); field.setValueSource("PROVIDER_PROFILE");
        field.setProfileField("gender"); field.setCode("GENDER");
        var provider = new ServiceProvider(); provider.setServiceProviderId(15L);
        when(profileService.findByUserId(5L)).thenReturn(provider);
        var assignment = new ServiceProviderTypeAssignment(); assignment.setAssignmentId(14L);
        assignment.setServiceProviderId(15L); assignment.setServiceProviderTypeId(1L);
        when(assignmentRepository.findByAssignmentIdAndServiceProviderId(14L, 15L)).thenReturn(Optional.of(assignment));
        when(fieldRepository.findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(
                1L, "PROVIDER_REQUIREMENT", "ACTIVE")).thenReturn(List.of(field));
        var stale = new ProviderCustomFieldSubmission(); stale.setFieldId(25L); stale.setValueJson("\"MALE\"");
        stale.setSubmissionId(99L); stale.setVerificationStatus("APPROVED");
        when(submissionRepository.findAllByAssignmentId(14L)).thenReturn(List.of(stale));
        when(profileFields.resolve(15L, field)).thenReturn(new ProviderProfileFields.Resolved(mapper.readTree("\"FEMALE\""), "PROVIDED"));
        var resolved = realService.getSubmissions(5L, 14L);
        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).value().asText()).isEqualTo("FEMALE");
        assertThat(resolved.get(0).submissionId()).isNull();
        assertThat(resolved.get(0).readOnly()).isTrue();
        assertThat(resolved.get(0).valueSource()).isEqualTo("PROVIDER_PROFILE");
        when(fieldRepository.findAllByFieldIdIn(any())).thenReturn(List.of(field));
        var duplicate = new com.jobhub.dto.provider.ProviderCustomSubmissionRequest(List.of(
                new com.jobhub.dto.provider.ProviderCustomSubmissionRequest.Entry(25L, mapper.readTree("\"MALE\""))));
        assertThatThrownBy(() -> realService.saveSubmissions(5L, 14L, duplicate)).hasMessageContaining("update the profile");
        verify(submissionRepository, never()).save(any());
    }

    @BeforeEach
    void typeExists() {
        lenient().when(typeRepository.findById(1L)).thenReturn(Optional.of(new ServiceProviderType()));
        activeOption(12L);
        activeOption(13L);
    }

    private ServiceCustomFieldRequest request(String options) throws Exception {
        return new ObjectMapper().readValue("""
                {"scope":"PROVIDER_REQUIREMENT","code":"EXPERIENCE",
                 "label":"Experience","fieldType":"NUMBER","required":true,
                """ + options + "}", ServiceCustomFieldRequest.class);
    }

    private void activeOption(long id) {
        ServiceOption option = new ServiceOption();
        option.setOptionId(id);
        option.setServiceProviderTypeId(1L);
        option.getServiceTypes().add(new com.jobhub.entity.provider.ServiceTypeOption(1L, 0, "ACTIVE"));
        option.setStatus("ACTIVE");
        lenient().when(optionRepository.findByOptionIdAndServiceProviderTypeId(id, 1L))
                .thenReturn(Optional.of(option));
    }

    @Test void linkedServiceCreatesAndReadsTheSameOptionOwnedField() throws Exception {
        var option = optionRepository.findByOptionIdAndServiceProviderTypeId(12L, 1L).orElseThrow();
        option.getServiceTypes().add(new ServiceTypeOption(2L, 0, "ACTIVE"));
        when(typeRepository.findById(2L)).thenReturn(Optional.of(new ServiceProviderType()));
        when(optionRepository.findByOptionIdAndServiceProviderTypeId(12L, 2L)).thenReturn(Optional.of(option));
        when(fieldRepository.save(any())).thenAnswer(i -> {
            ServiceCustomField field = i.getArgument(0); field.setFieldId(25L); return field;
        });
        var result = service.create(2L, request("\"optionIds\":[12]"));
        assertThat(result.serviceTypeId()).isEqualTo(1L);
        assertThat(result.optionIds()).containsExactly(12L);
        var saved = org.mockito.ArgumentCaptor.forClass(ServiceCustomField.class);
        verify(fieldRepository).save(saved.capture());
        assertThat(saved.getValue().getServiceProviderTypeId()).isEqualTo(1L);
        when(fieldRepository.findAllByServiceProviderTypeIdOrderByDisplayOrderAscLabelAsc(1L))
                .thenReturn(List.of(saved.getValue()));
        assertThat(service.listForOption(2L, 12L, false)).containsExactly(result);
    }

    @Test void linkedAssignmentCanSubmitAnInheritedCommonRequirementWithoutCopyingItsField() {
        var option=optionRepository.findByOptionIdAndServiceProviderTypeId(12L,1L).orElseThrow();
        option.getServiceTypes().add(new ServiceTypeOption(2L,0,"ACTIVE"));
        when(optionRepository.findByOptionIdAndServiceProviderTypeId(12L,2L)).thenReturn(Optional.of(option));
        var provider=new ServiceProvider();provider.setServiceProviderId(15L);
        when(profileService.findByUserId(5L)).thenReturn(provider);
        var assignment=new ServiceProviderTypeAssignment();assignment.setAssignmentId(14L);
        assignment.setServiceProviderId(15L);assignment.setServiceProviderTypeId(2L);
        when(assignmentRepository.findByAssignmentIdAndServiceProviderId(14L,15L)).thenReturn(Optional.of(assignment));
        var registration=new ProviderServiceOption();registration.setOptionId(12L);
        when(providerOptionRepository.findAllByAssignmentId(14L)).thenReturn(List.of(registration));
        var field=new ServiceCustomField();field.setFieldId(25L);field.setServiceProviderTypeId(1L);
        field.setScope("PROVIDER_REQUIREMENT");field.setStatus("ACTIVE");field.setCode("EXPERIENCE");field.setFieldType("NUMBER");
        when(fieldRepository.findAllByFieldIdIn(any())).thenReturn(List.of(field));
        service.saveSubmissions(5L,14L,new com.jobhub.dto.provider.ProviderCustomSubmissionRequest(List.of(
                new com.jobhub.dto.provider.ProviderCustomSubmissionRequest.Entry(25L,new com.fasterxml.jackson.databind.node.IntNode(1)))));
        verify(submissionRepository).save(argThat(a -> a.getFieldId().equals(25L) && a.getAssignmentId().equals(14L)));
    }

    @Test
    void createsOneFieldSharedBySeveralOptions() throws Exception {
        activeOption(12L);
        activeOption(13L);
        when(fieldRepository.save(any())).thenAnswer(invocation -> {
            ServiceCustomField field = invocation.getArgument(0);
            assertThat(field.appliesToOptions(Set.of(13L))).isTrue();
            assertThat(field.appliesToOptions(Set.of(14L))).isFalse();
            field.setFieldId(25L);
            return field;
        });
        var result = service.create(1L, request("\"optionIds\":[12,13]"));
        assertThat(result.fieldId()).isEqualTo(25L);
        assertThat(result.optionIds()).containsExactly(12L, 13L);
        verify(fieldRepository, times(1)).save(any());
    }

    @Test void allowsDisplayOnlyPublicImageFieldButRejectsPublicDocumentsAndBookingImages() throws Exception {
        var mapper = new ObjectMapper();
        var request = mapper.readValue("""
                {"scope":"PROVIDER_REQUIREMENT","code":"PORTFOLIO","label":"Portfolio",
                 "fieldType":"IMAGE_UPLOAD","customerVisible":true,"searchable":false,
                 "minimumFiles":0,"maximumFiles":5,"allowedFileTypes":["JPG","PNG","WEBP"]}
                """, ServiceCustomFieldRequest.class);
        when(fieldRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var saved = service.create(1L, request);
        assertThat(saved.customerVisible()).isTrue();
        assertThat(saved.searchable()).isFalse();
        assertThat(saved.maximumFiles()).isEqualTo(5);
        var json = mapper.valueToTree(request);
        ((com.fasterxml.jackson.databind.node.ObjectNode)json).put("fieldType", "DOCUMENT_UPLOAD");
        assertThatThrownBy(() -> service.create(1L, mapper.treeToValue(json, ServiceCustomFieldRequest.class)))
                .hasMessageContaining("unconditional");
        ((com.fasterxml.jackson.databind.node.ObjectNode)json).put("fieldType", "IMAGE_UPLOAD").put("scope", "BOOKING_FIELD");
        assertThatThrownBy(() -> service.create(1L, mapper.treeToValue(json, ServiceCustomFieldRequest.class)))
                .hasMessageContaining("unconditional");
    }

    @Test void rejectsSearchOnPrivateUploadAndConditionalFields() throws Exception {
        assertThatThrownBy(() -> service.create(1L, request("\"searchable\":true")))
                .hasMessageContaining("customer visible");
        var upload = new ObjectMapper().readValue("""
                {"scope":"PROVIDER_REQUIREMENT","code":"NIC","label":"NIC",
                 "fieldType":"IMAGE_UPLOAD","customerVisible":true,"searchable":true}
                """, ServiceCustomFieldRequest.class);
        assertThatThrownBy(() -> service.create(1L, upload)).hasMessageContaining("display-only");
        assertThatThrownBy(() -> service.create(1L,
                request("\"searchable\":true,\"customerVisible\":true,\"condition\":{}")))
                .hasMessageContaining("unconditional");
    }

    @Test void richChoicesRoundTripAndProvidersSubmitOnlyTheirValues() throws Exception {
        var mapper = new ObjectMapper();
        var realService = new ServiceCustomFieldService(mapper, typeRepository, optionRepository,
                fieldRepository, submissionRepository, providerOptionRepository, assignmentRepository,
                profileService, approvalService, serviceSetupService, profileFields);
        var definition = mapper.readValue("""
                {"scope":"PROVIDER_REQUIREMENT","code":"VEHICLE","label":"Vehicle",
                 "fieldType":"SELECT","customerVisible":true,"searchable":true,
                 "options":["BIKE",{"value":"CAR","label":"Car","icon":{
                 "key":"service-icons/car.png","url":"https://example.com/car.png"}}]}
                """, ServiceCustomFieldRequest.class);
        var saved = new java.util.concurrent.atomic.AtomicReference<ServiceCustomField>();
        when(fieldRepository.save(any())).thenAnswer(i -> {
            ServiceCustomField item = i.getArgument(0); item.setFieldId(25L); saved.set(item); return item;
        });
        var response = realService.create(1L, definition);
        assertThat(response.options()).isEqualTo(definition.options());
        assertThat(response.options().get(1).path("icon").path("key").asText()).isEqualTo("service-icons/car.png");
        var provider = new ServiceProvider(); provider.setServiceProviderId(15L);
        when(profileService.findByUserId(5L)).thenReturn(provider);
        var assignment = new ServiceProviderTypeAssignment();
        assignment.setAssignmentId(14L); assignment.setServiceProviderId(15L); assignment.setServiceProviderTypeId(1L);
        when(assignmentRepository.findByAssignmentIdAndServiceProviderId(14L, 15L)).thenReturn(Optional.of(assignment));
        when(fieldRepository.findAllByFieldIdIn(any())).thenAnswer(i -> List.of(saved.get()));
        var valid = new com.jobhub.dto.provider.ProviderCustomSubmissionRequest(List.of(
                new com.jobhub.dto.provider.ProviderCustomSubmissionRequest.Entry(25L, mapper.readTree("\"CAR\""))));
        realService.saveSubmissions(5L, 14L, valid);
        verify(submissionRepository).save(argThat(s -> "\"CAR\"".equals(s.getValueJson())));
        for (String invalid : List.of("\"Car\"", "\"UNKNOWN\"", "{\"value\":\"CAR\"}")) {
            var request = new com.jobhub.dto.provider.ProviderCustomSubmissionRequest(List.of(
                    new com.jobhub.dto.provider.ProviderCustomSubmissionRequest.Entry(25L, mapper.readTree(invalid))));
            assertThatThrownBy(() -> realService.saveSubmissions(5L, 14L, request)).isInstanceOf(IllegalArgumentException.class);
        }
        saved.get().setFieldType("MULTI_SELECT");
        realService.saveSubmissions(5L, 14L, new com.jobhub.dto.provider.ProviderCustomSubmissionRequest(List.of(
                new com.jobhub.dto.provider.ProviderCustomSubmissionRequest.Entry(25L, mapper.readTree("[\"CAR\",\"BIKE\"]")))));
        verify(submissionRepository).save(argThat(s -> "[\"CAR\",\"BIKE\"]".equals(s.getValueJson())));
    }

    @Test void fieldIconsPersistAcrossSchemasAndCanBeRemoved() throws Exception {
        var realMapper = new ObjectMapper();
        var realService = new ServiceCustomFieldService(realMapper, typeRepository, optionRepository,
                fieldRepository, submissionRepository, providerOptionRepository, assignmentRepository,
                profileService, approvalService, serviceSetupService, profileFields);
        var saved = new java.util.concurrent.atomic.AtomicReference<ServiceCustomField>();
        when(fieldRepository.save(any())).thenAnswer(i -> {
            ServiceCustomField item = i.getArgument(0);
            item.setFieldId(25L); saved.set(item); return item;
        });
        var definition = request("\"icon\":{\"key\":\"service-icons/experience.png\","
                + "\"url\":\"https://example.com/experience.png\",\"altText\":\"Experience\"}");
        var created = realService.create(1L, definition);
        assertThat(created.icon().key()).isEqualTo("service-icons/experience.png");
        assertThat(definition.withOptionIds(List.of(12L)).icon()).isEqualTo(created.icon());
        when(fieldRepository.findAllByServiceProviderTypeIdOrderByDisplayOrderAscLabelAsc(1L))
                .thenAnswer(i -> List.of(saved.get()));
        assertThat(realService.listForOption(1L, 12L, false).get(0).icon()).isEqualTo(created.icon());
        when(fieldRepository.findByFieldIdAndServiceProviderTypeId(25L, 1L))
                .thenAnswer(i -> Optional.of(saved.get()));
        var bookingField = realMapper.readValue("""
                {"scope":"BOOKING_FIELD","code":"CARE_NOTES","label":"Care notes",
                 "fieldType":"TEXTAREA","icon":{"key":"service-icons/notes.png",
                 "url":"https://example.com/notes.png"}}
                """, ServiceCustomFieldRequest.class);
        assertThat(realService.update(1L, 25L, bookingField).icon().key()).isEqualTo("service-icons/notes.png");
        assertThat(realService.update(1L, 25L, request("\"icon\":null")).icon()).isNull();
        assertThat(saved.get().getIconJson()).isNull();
    }

    @Test void nestedIconRequiresKeyAndUrlButIconItselfIsOptional() throws Exception {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request("\"icon\":null"))).isEmpty();
            assertThat(validator.validate(request("\"icon\":{\"key\":\"\",\"url\":\"\"}")))
                    .extracting(v -> v.getPropertyPath().toString()).contains("icon.key", "icon.url");
        }
    }

    @Test void resolvesApprovedReusableAnswersButKeepsPendingExactOverride() throws Exception {
        var assignment = new ServiceProviderTypeAssignment();
        assignment.setAssignmentId(14L);
        assignment.setServiceProviderId(15L);
        assignment.setServiceProviderTypeId(1L);
        var field = new ServiceCustomField();
        field.setFieldId(25L); field.setCode("GENDER"); field.setFieldType("SELECT");
        field.setReuseScope("PROVIDER"); field.setScope("PROVIDER_REQUIREMENT");
        var source = new ProviderCustomFieldSubmission();
        source.setSubmissionId(2L); source.setAssignmentId(99L);
        source.setReusableFieldType("SELECT"); source.setVerificationStatus("APPROVED");
        source.setValueJson("\"FEMALE\"");
        when(fieldRepository.findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(
                1L, "PROVIDER_REQUIREMENT", "ACTIVE")).thenReturn(List.of(field));
        when(submissionRepository.findAllByProviderIdAndReusableCodeAndVerificationStatusOrderByUpdatedAtDesc(
                15L, "GENDER", "APPROVED")).thenReturn(List.of(source));
        when(objectMapper.readTree(anyString())).thenAnswer(i -> new ObjectMapper().readTree((String) i.getArgument(0)));
        var inherited = service.resolvedProviderAnswers(assignment);
        assertThat(inherited.get(0).reused()).isTrue();
        assertThat(inherited.get(0).fieldId()).isEqualTo(25L);
        assertThat(inherited.get(0).sourceAssignmentId()).isEqualTo(99L);

        var exact = new ProviderCustomFieldSubmission();
        exact.setFieldId(25L); exact.setAssignmentId(14L);
        exact.setVerificationStatus("PENDING"); exact.setValueJson("\"MALE\"");
        when(submissionRepository.findAllByAssignmentId(14L)).thenReturn(List.of(exact));
        var overridden = service.resolvedProviderAnswers(assignment);
        assertThat(overridden.get(0).reused()).isFalse();
        assertThat(overridden.get(0).verificationStatus()).isEqualTo("PENDING");
    }

    @Test
    void rejectsDuplicateAndMixedOptionProperties() throws Exception {
        var duplicate = request("\"optionIds\":[12,12]");
        var mixed = request("\"optionId\":12,\"optionIds\":[13]");
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(1L, duplicate));
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(1L, mixed));
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void optionReadIncludesCommonAndSharedFieldsAndFiltersInactiveForProviders() {
        ServiceCustomField common = new ServiceCustomField();
        common.setFieldId(1L);
        common.setStatus("ACTIVE");
        ServiceCustomField shared = new ServiceCustomField();
        shared.setFieldId(2L);
        shared.setStatus("ACTIVE");
        shared.setOptionIds(Set.of(12L, 13L));
        ServiceCustomField inactive = new ServiceCustomField();
        inactive.setFieldId(3L);
        inactive.setStatus("INACTIVE");
        inactive.setOptionId(12L);
        ServiceCustomField unrelated = new ServiceCustomField();
        unrelated.setFieldId(4L);
        unrelated.setStatus("ACTIVE");
        unrelated.setOptionId(14L);
        when(fieldRepository.findAllByServiceProviderTypeIdOrderByDisplayOrderAscLabelAsc(1L))
                .thenReturn(List.of(common, shared, inactive, unrelated));
        assertThat(service.listForOption(1L, 12L, false)).extracting(item -> item.fieldId())
                .containsExactly(1L, 2L);
        assertThat(service.listForOption(1L, 12L, true)).extracting(item -> item.fieldId())
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void optionEditCreatesFieldsForCurrentOptionWhenIdsAreOmitted() throws Exception {
        var spyService = spy(service);
        doReturn(null).when(spyService).create(any(), any());
        spyService.saveForOption(1L, 12L, List.of(new OptionDynamicFieldRequest(null,
                request("\"displayOrder\":1"))));
        verify(spyService).create(eq(1L), argThat(definition ->
                definition.optionIds().equals(List.of(12L))));
    }

    @Test
    void optionEditPreservesSharedFieldAssignmentsWhenIdsAreOmitted() throws Exception {
        ServiceCustomField field = new ServiceCustomField();
        field.setOptionIds(Set.of(12L, 13L));
        when(fieldRepository.findByFieldIdAndServiceProviderTypeId(25L, 1L))
                .thenReturn(Optional.of(field));
        var spyService = spy(service);
        doReturn(null).when(spyService).update(any(), any(), any());
        spyService.saveForOption(1L, 12L, List.of(new OptionDynamicFieldRequest(25L,
                request("\"displayOrder\":1"))));
        verify(spyService).update(eq(1L), eq(25L), argThat(definition ->
                Set.copyOf(definition.optionIds()).equals(Set.of(12L, 13L))));
    }

    @Test
    void optionEditCannotChangeAnUnrelatedField() throws Exception {
        ServiceCustomField field = new ServiceCustomField();
        field.setOptionIds(Set.of(13L));
        when(fieldRepository.findByFieldIdAndServiceProviderTypeId(25L, 1L))
                .thenReturn(Optional.of(field));
        var definition = request("\"displayOrder\":1");
        assertThatIllegalArgumentException().isThrownBy(() -> service.saveForOption(1L, 12L,
                List.of(new OptionDynamicFieldRequest(25L, definition))))
                .withMessage("Dynamic field does not belong to this option");
    }

    @Test
    void rejectsOptionsOutsideTheService() throws Exception {
        var request = request("\"optionIds\":[99]");
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(1L, request))
                .withMessage("Service option is invalid");
        verify(fieldRepository, never()).save(any());
    }

    @Test
    void legacySingleOptionRemainsOptionSpecific() throws Exception {
        activeOption(12L);
        when(fieldRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.create(1L, request("\"optionId\":12"));
        assertThat(result.optionId()).isEqualTo(12L);
        assertThat(result.optionIds()).containsExactly(12L);
    }

    @Test
    void schemaGroupsSharedFieldUnderEachOptionRatherThanCommonFields() {
        activeOption(12L);
        activeOption(13L);
        ServiceCustomField field = new ServiceCustomField();
        field.setFieldId(25L);
        field.setOptionIds(Set.of(12L, 13L));
        field.setScope("PROVIDER_REQUIREMENT"); field.setStatus("ACTIVE");
        when(fieldRepository.findAllByServiceProviderTypeIdOrderByDisplayOrderAscLabelAsc(1L)).thenReturn(List.of(field));
        when(fieldRepository.findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(
                1L, "PROVIDER_REQUIREMENT", "ACTIVE")).thenReturn(List.of(field));
        var options = List.of(optionRepository.findByOptionIdAndServiceProviderTypeId(12L, 1L).orElseThrow(),
                optionRepository.findByOptionIdAndServiceProviderTypeId(13L, 1L).orElseThrow());
        when(optionRepository.findAllByServiceProviderTypeIdAndStatusOrderByDisplayOrderAscNameAsc(1L, "ACTIVE"))
                .thenReturn(options);
        var result = service.schema(1L, "PROVIDER_REQUIREMENT");
        assertThat(result.commonFields()).isEmpty();
        assertThat(result.options()).allSatisfy(option ->
                assertThat(option.fields()).extracting(item -> item.fieldId()).containsExactly(25L));
    }
}
