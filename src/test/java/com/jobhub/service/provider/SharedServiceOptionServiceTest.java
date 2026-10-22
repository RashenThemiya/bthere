package com.jobhub.service.provider;

import com.jobhub.dto.provider.LinkServiceOptionRequest;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SharedServiceOptionServiceTest {
    @Mock ServiceProviderTypeRepository types;
    @Mock ServiceOptionRepository options;
    @Mock ServiceCustomFieldService fields;
    @InjectMocks ServiceOptionService service;

    private ServiceOption option() {
        var option = new ServiceOption();
        option.setOptionId(12L);
        option.setServiceProviderTypeId(1L);
        option.setCode("MEAL_PREPARATION");
        option.setName("Meal preparation");
        option.setStatus("ACTIVE");
        option.getServiceTypes().add(new ServiceTypeOption(1L, 4, "ACTIVE"));
        return option;
    }

    @Test void linksSameOptionAndReturnsTargetServiceFields() {
        var option = option();
        when(types.findById(2L)).thenReturn(Optional.of(new ServiceProviderType()));
        when(options.findById(12L)).thenReturn(Optional.of(option));
        when(options.save(option)).thenReturn(option);

        var response = service.link(2L, 12L, new LinkServiceOptionRequest(8));

        assertThat(response.optionId()).isEqualTo(12L);
        assertThat(response.serviceTypeId()).isEqualTo(2L);
        assertThat(response.displayOrder()).isEqualTo(8);
        assertThat(option.activeForService(1L)).isTrue();
        assertThat(option.activeForService(2L)).isTrue();
        assertThat(option.serviceLink(1L).getDisplayOrder()).isEqualTo(4);
        verify(fields).listForOption(2L, 12L, true);
        verify(fields, never()).saveForOption(any(), any(), any());
    }

    @Test void unlinkDisablesOnlyRequestedMembershipAndRelinkPreservesOrder() {
        var option = option();
        option.getServiceTypes().add(new ServiceTypeOption(2L, 8, "ACTIVE"));
        when(options.findByOptionIdAndServiceProviderTypeId(12L, 2L)).thenReturn(Optional.of(option));
        when(options.save(option)).thenReturn(option);
        service.unlink(2L, 12L);
        assertThat(option.activeForService(1L)).isTrue();
        assertThat(option.activeForService(2L)).isFalse();

        when(types.findById(2L)).thenReturn(Optional.of(new ServiceProviderType()));
        when(options.findById(12L)).thenReturn(Optional.of(option));
        var response = service.link(2L, 12L, new LinkServiceOptionRequest(null));
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.displayOrder()).isEqualTo(8);
        assertThat(option.getServiceTypes()).hasSize(2);
        verify(options, never()).delete(any());
    }

    @Test void refusesConflictingCodeInTargetService() {
        var option = option();
        when(types.findById(2L)).thenReturn(Optional.of(new ServiceProviderType()));
        when(options.findById(12L)).thenReturn(Optional.of(option));
        when(options.existsByServiceProviderTypeIdAndCodeIgnoreCase(2L, option.getCode())).thenReturn(true);
        assertThatThrownBy(() -> service.link(2L, 12L, new LinkServiceOptionRequest(null)))
                .isInstanceOf(ConflictException.class);
        assertThat(option.getServiceTypes()).hasSize(1);
        verify(options, never()).save(any());
    }

    @Test void cannotUnlinkAnUnrelatedService() {
        assertThatThrownBy(() -> service.unlink(2L, 12L)).isInstanceOf(ResourceNotFoundException.class);
        verify(options, never()).save(any());
    }

    @Test void sharedCodeRenameChecksOtherLinkedServices() {
        var option = option();
        option.getServiceTypes().add(new ServiceTypeOption(2L, 8, "INACTIVE"));
        when(options.findByOptionIdAndServiceProviderTypeId(12L, 1L)).thenReturn(Optional.of(option));
        when(options.existsByServiceProviderTypeIdAndCodeIgnoreCase(1L, "NEW_CODE")).thenReturn(false);
        when(options.existsByServiceProviderTypeIdAndCodeIgnoreCase(2L, "NEW_CODE")).thenReturn(true);
        var request = new com.jobhub.dto.provider.CreateServiceOptionRequest(
                "NEW_CODE", "Meal", null, 0, "TIME_BASED", null, false,
                Set.of("ONLINE"), "ONE_TO_ONE", 1, 1, "MANY_AT_A_TIME", null, null,
                null, null, null, List.of(), List.of(), null);
        assertThatThrownBy(() -> service.update(1L, 12L, request)).isInstanceOf(ConflictException.class);
        assertThat(option.getCode()).isEqualTo("MEAL_PREPARATION");
        verify(options, never()).save(any());
    }

    @Test void markerIconsAndFieldSelectionAreSharedDespiteLegacyLinkMetadata() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var option = option();
        option.getServiceTypes().add(new ServiceTypeOption(2L, 8, "ACTIVE"));
        option.serviceLink(2L).setMapMarkerFieldId(55L);
        when(options.findByOptionIdAndServiceProviderTypeId(12L, 1L)).thenReturn(Optional.of(option));
        when(options.save(option)).thenReturn(option);
        var field = mapper.readValue("""
                {"fieldId":45,"scope":"PROVIDER_REQUIREMENT","status":"ACTIVE","fieldType":"SELECT",
                 "customerVisible":true,"searchable":true}
                """, com.jobhub.dto.provider.ServiceCustomFieldResponse.class);
        when(fields.listForOption(eq(1L), eq(12L), anyBoolean())).thenReturn(List.of(field));
        var request = mapper.readValue("""
                {"code":"MEAL_PREPARATION","name":"Meal preparation","gallery":[],"responsibilities":[],
                 "icon":{"key":"card.png","url":"https://example.com/card.png"},
                 "mapMarkerIcon":{"key":"marker.png","url":"https://example.com/marker.png"},"mapMarkerFieldId":45}
                """, com.jobhub.dto.provider.CreateServiceOptionRequest.class);
        var response = service.update(1L, 12L, request);
        assertThat(response.icon().key()).isEqualTo("card.png");
        assertThat(response.mapMarkerIcon().key()).isEqualTo("marker.png");
        assertThat(response.mapMarkerFieldId()).isEqualTo(45);
        assertThat(option.getMapMarkerFieldId()).isEqualTo(45);
        assertThat(response.configurationServiceTypeId()).isEqualTo(1L);
        assertThat(option.serviceLink(2L).getMapMarkerFieldId()).isEqualTo(55);

        var cleared = mapper.readValue("""
                {"code":"MEAL_PREPARATION","name":"Meal preparation","gallery":[],"responsibilities":[],
                 "icon":{"key":"card.png","url":"https://example.com/card.png"}}
                """, com.jobhub.dto.provider.CreateServiceOptionRequest.class);
        response = service.update(1L, 12L, cleared);
        assertThat(response.mapMarkerIcon()).isNull();
        assertThat(response.mapMarkerFieldId()).isNull();
        assertThat(response.icon().key()).isEqualTo("card.png");
        assertThat(option.serviceLink(2L).getMapMarkerFieldId()).isEqualTo(55);
    }

    @Test void linkingRejectsAnUnknownMapMarkerField() {
        var option = option();
        when(types.findById(2L)).thenReturn(Optional.of(new ServiceProviderType()));
        when(options.findById(12L)).thenReturn(Optional.of(option));
        assertThatIllegalArgumentException().isThrownBy(() ->
                service.link(2L, 12L, new LinkServiceOptionRequest(0, 999L)));
        verify(options, never()).save(any());
    }
}
