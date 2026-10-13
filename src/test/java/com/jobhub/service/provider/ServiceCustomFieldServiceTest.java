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
    @InjectMocks ServiceCustomFieldService service;

    @BeforeEach
    void typeExists() {
        lenient().when(typeRepository.findById(1L)).thenReturn(Optional.of(new ServiceProviderType()));
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
        option.setStatus("ACTIVE");
        when(optionRepository.findByOptionIdAndServiceProviderTypeId(id, 1L))
                .thenReturn(Optional.of(option));
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
