package com.jobhub.service.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.market.PricingConditionRequest;
import com.jobhub.entity.job.*;
import com.jobhub.entity.provider.ServiceCustomField;
import com.jobhub.exception.ConflictException;
import com.jobhub.repository.job.JobRateRepository;
import com.jobhub.repository.provider.ServiceCustomFieldRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CombinationPricingRulesTest {
    @Mock ServiceCustomFieldRepository fields;
    @Mock JobRateRepository rates;
    CombinationPricingRules service;

    @BeforeEach void setup() { service = new CombinationPricingRules(fields, rates, new ObjectMapper()); }

    ServiceCustomField field(long id, String choices) {
        var f = new ServiceCustomField();
        f.setFieldId(id); f.setServiceProviderTypeId(2L); f.setOptionId(12L);
        f.setScope("PROVIDER_REQUIREMENT"); f.setStatus("ACTIVE"); f.setFieldType("MULTI_SELECT");
        f.setCustomerVisible(true); f.setSearchable(true); f.setOptionsJson(choices);
        when(fields.findByFieldIdAndServiceProviderTypeId(id, 2L)).thenReturn(Optional.of(f));
        return f;
    }

    JobRate rate(PricingConditionRequest... conditions) {
        var r = new JobRate(); r.setMarketId(1L); r.setServiceProviderTypeId(2L);
        r.setServiceOptionId(12L); r.setStatus("ACTIVE");
        r.setEffectiveFrom(LocalDateTime.of(2026, 1, 1, 0, 0));
        for (var c : conditions) r.getConditions().add(new JobRateCondition(c.fieldId(), c.choiceValue()));
        return r;
    }

    @Test void acceptsExistingObjectChoicesAndStoresSortedConditions() {
        field(45, "[{\"value\":\"CAR\",\"label\":\"Car\",\"icon\":{\"key\":\"car.png\",\"url\":\"https://example.com/car.png\"}}]");
        field(46, "[\"MALE\",\"FEMALE\"]");
        var rate = rate();
        service.configure(rate, List.of(new PricingConditionRequest(46L, "MALE"), new PricingConditionRequest(45L, "CAR")));
        assertThat(rate.getConditions()).extracting(JobRateCondition::getFieldId).containsExactly(45L, 46L);
    }

    @Test void rejectsDuplicateFieldChoices() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.normalize(List.of(
                new PricingConditionRequest(45L, "CAR"), new PricingConditionRequest(45L, "BIKE"))));
    }

    @Test void rejectsUnknownChoiceAndUnrelatedOption() {
        var field = field(45, "[\"CAR\"]");
        var selection = List.of(new PricingConditionRequest(45L, "BIKE"));
        assertThatIllegalArgumentException().isThrownBy(() -> service.validateChoices(2L, 12L, selection));
        field.setOptionId(99L);
        assertThatIllegalArgumentException().isThrownBy(() -> service.validateChoices(2L, 12L,
                List.of(new PricingConditionRequest(45L, "CAR"))));
    }

    @Test void rejectsPrivateInactiveAndConditionalFields() {
        var field = field(45, "[\"CAR\"]");
        var choices = List.of(new PricingConditionRequest(45L, "CAR"));
        field.setCustomerVisible(false);
        assertThatIllegalArgumentException().isThrownBy(() -> service.validateChoices(2L, 12L, choices));
        field.setCustomerVisible(true); field.setStatus("INACTIVE");
        assertThatIllegalArgumentException().isThrownBy(() -> service.validateChoices(2L, 12L, choices));
        field.setStatus("ACTIVE"); field.setConditionJson("{\"field\":\"OTHER\"}");
        assertThatIllegalArgumentException().isThrownBy(() -> service.validateChoices(2L, 12L, choices));
    }

    @Test void rejectsAmbiguousCrossFieldRulesOfEqualSpecificity() {
        field(45, "[\"CAR\"]");
        var other = rate(new PricingConditionRequest(46L, "MALE"));
        when(rates.findAllByMarketIdAndServiceProviderTypeIdAndServiceOptionIdAndStatus(1L, 2L, 12L, "ACTIVE"))
                .thenReturn(List.of(other));
        assertThatThrownBy(() -> service.configure(rate(), List.of(new PricingConditionRequest(45L, "CAR"))))
                .isInstanceOf(ConflictException.class);
    }

    @Test void allowsDifferentChoicesAndNonOverlappingEffectiveWindows() {
        field(45, "[\"CAR\",\"BIKE\"]");
        var other = rate(new PricingConditionRequest(45L, "BIKE"));
        when(rates.findAllByMarketIdAndServiceProviderTypeIdAndServiceOptionIdAndStatus(1L, 2L, 12L, "ACTIVE"))
                .thenReturn(List.of(other));
        service.configure(rate(), List.of(new PricingConditionRequest(45L, "CAR")));
        other.getConditions().get(0).setChoiceValue("CAR");
        other.setEffectiveTo(LocalDateTime.of(2025, 12, 31, 23, 59));
        service.configure(rate(), List.of(new PricingConditionRequest(45L, "CAR")));
    }

    @Test void permitsMoreSpecificCombinationsAndGeographicOverrides() {
        field(45, "[\"CAR\"]"); field(46, "[\"MALE\"]");
        var other = rate(new PricingConditionRequest(45L, "CAR"));
        when(rates.findAllByMarketIdAndServiceProviderTypeIdAndServiceOptionIdAndStatus(1L, 2L, 12L, "ACTIVE"))
                .thenReturn(List.of(other));
        service.configure(rate(), List.of(new PricingConditionRequest(45L, "CAR"), new PricingConditionRequest(46L, "MALE")));
        var area = rate(); area.setAreaLatitude(BigDecimal.ZERO); area.setAreaLongitude(BigDecimal.ZERO);
        area.setAreaRadiusKm(BigDecimal.TEN);
        service.configure(area, List.of(new PricingConditionRequest(45L, "CAR")));
    }
}
