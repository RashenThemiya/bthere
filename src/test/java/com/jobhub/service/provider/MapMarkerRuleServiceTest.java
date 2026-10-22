package com.jobhub.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.market.PricingConditionRequest;
import com.jobhub.dto.provider.*;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.*;
import com.jobhub.repository.provider.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MapMarkerRuleServiceTest {
    @Mock ServiceOptionRepository options;
    @Mock ServiceOptionMapMarkerRuleRepository rules;
    @Mock ServiceCustomFieldService fields;
    @InjectMocks MapMarkerRuleService service;
    final ObjectMapper mapper = new ObjectMapper();

    void scope() throws Exception {
        var option = new ServiceOption(); option.setOptionId(12L);
        option.setServiceProviderTypeId(1L);
        option.getServiceTypes().add(new ServiceTypeOption(1L, 0, "ACTIVE"));
        when(options.findLockedById(12L)).thenReturn(Optional.of(option));
    }
    void definitions() throws Exception {
        var list = new ArrayList<ServiceCustomFieldResponse>();
        for (long id : List.of(45L,47L,48L)) list.add(mapper.readValue("""
                {"fieldId":%d,"scope":"PROVIDER_REQUIREMENT","status":"ACTIVE","fieldType":"SELECT",
                 "customerVisible":true,"searchable":true,"options":["CAR","BIKE","ELECTRIC","PETROL"]}
                """.formatted(id), ServiceCustomFieldResponse.class));
        when(fields.listForOption(1L, 12L, false)).thenReturn(list);
    }
    PricingConditionRequest choice(long fieldId, String value) { return new PricingConditionRequest(fieldId, value); }
    MapMarkerRuleRequest request(String status, PricingConditionRequest... conditions) {
        return new MapMarkerRuleRequest(List.of(conditions),
                new ServiceMediaPayload("markers/car.png", "https://example.com/car.png", "Car", 0), status);
    }
    ServiceOptionMapMarkerRule rule(long id, PricingConditionRequest... conditions) {
        var rule = new ServiceOptionMapMarkerRule(); rule.setMarkerRuleId(id);
        rule.setServiceProviderTypeId(1L); rule.setOptionId(12L);
        for (var condition : conditions) rule.getConditions().add(new MapMarkerCondition(condition.fieldId(), condition.choiceValue()));
        return rule;
    }

    @Test void createsScopedRuleWithSortedConditionsAndOwnMarkerAsset() throws Exception {
        scope(); definitions();
        when(rules.save(any())).thenAnswer(i -> {
            ServiceOptionMapMarkerRule rule = i.getArgument(0); rule.setMarkerRuleId(7L); return rule;
        });
        var response = service.create(1L, 12L, request(null, choice(47,"ELECTRIC"), choice(45,"CAR")));
        assertThat(response.ruleId()).isEqualTo(7);
        assertThat(response.serviceTypeId()).isEqualTo(1);
        assertThat(response.conditions()).extracting(PricingConditionRequest::fieldId).containsExactly(45L,47L);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.mapMarkerIcon().key()).isEqualTo("markers/car.png");
        verify(options).findLockedById(12L);
    }

    @Test void rejectsOverlappingEquallySpecificRulesButAllowsDifferentChoices() throws Exception {
        scope(); definitions();
        when(rules.findAllByServiceProviderTypeIdAndOptionIdAndStatus(1L,12L,"ACTIVE"))
                .thenReturn(List.of(rule(7, choice(47,"ELECTRIC"))));
        assertThatThrownBy(() -> service.create(1L,12L,request(null,choice(45,"CAR"))))
                .isInstanceOf(ConflictException.class);
        when(rules.findAllByServiceProviderTypeIdAndOptionIdAndStatus(1L,12L,"ACTIVE"))
                .thenReturn(List.of(rule(7, choice(45,"BIKE"))));
        when(rules.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThatCode(() -> service.create(1L,12L,request(null,choice(45,"CAR")))).doesNotThrowAnyException();
    }

    @Test void moreSpecificCombinationMayOverrideGeneralRule() throws Exception {
        scope(); definitions();
        when(rules.findAllByServiceProviderTypeIdAndOptionIdAndStatus(1L,12L,"ACTIVE"))
                .thenReturn(List.of(rule(7,choice(45,"CAR"))));
        when(rules.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThatCode(() -> service.create(1L,12L,request(null,choice(45,"CAR"),choice(47,"ELECTRIC"))))
                .doesNotThrowAnyException();
    }

    @Test void rejectsUnknownChoicesDuplicateFieldsAndEmptyConditions() throws Exception {
        scope(); definitions();
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(1L,12L,request(null,choice(45,"UNKNOWN"))));
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(1L,12L,request(null,choice(45,"CAR"),choice(45,"BIKE"))));
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(1L,12L,request(null)));
        verify(rules,never()).save(any());
    }

    @Test void unknownOrPrivateFieldsCannotBeUsed() throws Exception {
        scope();
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(1L,12L,request(null,choice(999,"CAR"))));
        verify(rules,never()).save(any());
    }

    @Test void fullUpdateReplacesConditionsAndSupportsDeactivationAndReactivationChecks() throws Exception {
        scope(); definitions();
        var existing = rule(7,choice(45,"CAR"));
        when(rules.findById(7L)).thenReturn(Optional.of(existing));
        when(rules.save(existing)).thenReturn(existing);
        var response = service.update(1L,12L,7L,request("INACTIVE",choice(47,"ELECTRIC")));
        assertThat(response.status()).isEqualTo("INACTIVE");
        assertThat(response.conditions()).extracting(PricingConditionRequest::fieldId).containsExactly(47L);
        when(rules.findAllByServiceProviderTypeIdAndOptionIdAndStatus(1L,12L,"ACTIVE"))
                .thenReturn(List.of(rule(8,choice(47,"ELECTRIC"))));
        assertThatThrownBy(() -> service.update(1L,12L,7L,request("ACTIVE",choice(47,"ELECTRIC"))))
                .isInstanceOf(ConflictException.class);
    }

    @Test void updateAndDeleteRejectAnotherServiceRule() throws Exception {
        scope();
        var other = rule(7); other.setServiceProviderTypeId(2L);
        when(rules.findById(7L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.delete(1L,12L,7L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(1L,12L,7L,request(null,choice(45,"CAR"))))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(rules,never()).delete(any());
    }

    @Test void sharedOptionCannotBeConfiguredUnderUnlinkedService() throws Exception {
        scope();
        assertThatThrownBy(() -> service.create(2L,12L,request(null,choice(45,"CAR"))))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(fields,rules);
    }

    @Test void deleteRemovesOnlyTheRequestedRule() throws Exception {
        scope();
        var rule = rule(7); when(rules.findById(7L)).thenReturn(Optional.of(rule));
        service.delete(1L,12L,7L);
        verify(rules).delete(rule);
        verifyNoInteractions(fields);
    }
}
