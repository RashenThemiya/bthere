package com.jobhub.service.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.customer.CustomerDiscoveryApi.ProviderFilter;
import com.jobhub.dto.provider.ServiceCustomFieldResponse;
import com.jobhub.entity.provider.*;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapMarkerResolverTest {
    final ObjectMapper mapper = new ObjectMapper();
    final ServiceProviderTypeRepository types = mock(ServiceProviderTypeRepository.class);
    final com.jobhub.repository.provider.ServiceOptionMapMarkerRuleRepository rules = mock(com.jobhub.repository.provider.ServiceOptionMapMarkerRuleRepository.class);
    final MapMarkerResolver resolver = new MapMarkerResolver(types, rules);

    ServiceOption option() {
        var option = new ServiceOption();
        option.setOptionId(12L);
        option.getServiceTypes().add(new ServiceTypeOption(1L, 0, "ACTIVE"));
        option.serviceLink(1L).setMapMarkerFieldId(45L);
        return option;
    }

    ServiceMediaValue media(String type, String key) {
        var icon = new ServiceMediaValue(); icon.setMediaType(type); icon.setObjectKey(key);
        icon.setUrl("https://example.com/" + key); icon.setDisplayOrder(0); return icon;
    }

    ServiceCustomFieldResponse field(long id, String choices) throws Exception {
        return mapper.readValue("""
                {"fieldId":%d,"scope":"PROVIDER_REQUIREMENT","status":"ACTIVE",
                 "fieldType":"MULTI_SELECT","customerVisible":true,"searchable":true,"options":%s}
                """.formatted(id, choices), ServiceCustomFieldResponse.class);
    }

    ProviderFilter filter(long id, String choices) throws Exception {
        return new ProviderFilter(id, mapper.readTree(choices), null, null);
    }

    ServiceOptionMapMarkerRule rule(long id, long[] fieldIds, String... values) {
        var rule = new ServiceOptionMapMarkerRule(); rule.setMarkerRuleId(id);
        rule.setMarkerIcon(media("MAP_MARKER", "combination-" + id + ".png"));
        for (int i = 0; i < values.length; i++) rule.getConditions().add(new MapMarkerCondition(fieldIds[i], values[i]));
        return rule;
    }

    @Test void mostSpecificCombinationOverridesSingleChoiceAndLessSpecificRule() throws Exception {
        var vehicle = field(45, "[{\"value\":\"CAR\",\"mapMarkerIcon\":{\"key\":\"car.png\",\"url\":\"https://example.com/car.png\"}}]");
        var fuel = field(47, "[\"ELECTRIC\",\"PETROL\"]");
        when(rules.findAllByServiceProviderTypeIdAndOptionIdAndStatus(1L, 12L, "ACTIVE")).thenReturn(List.of(
                rule(1, new long[]{45}, "CAR"), rule(2, new long[]{45,47}, "CAR", "ELECTRIC")));
        var result = resolver.resolve(1L, option(), List.of(vehicle, fuel),
                List.of(filter(45, "[\"CAR\"]"), filter(47, "[\"ELECTRIC\"]")));
        assertThat(result.source()).isEqualTo("COMBINATION");
        assertThat(result.ruleId()).isEqualTo(2);
        assertThat(result.icon().key()).isEqualTo("combination-2.png");
        assertThat(result.conditions()).hasSize(2);
        verifyNoInteractions(types);
    }

    @Test void multipleValuesDoNotImplyASpecificCombinationAndKeepFallback() throws Exception {
        var option = option(); option.getMedia().add(media("MAP_MARKER", "ride.png"));
        var fields = List.of(field(45, "[\"CAR\"]"), field(47, "[\"ELECTRIC\",\"PETROL\"]"));
        when(rules.findAllByServiceProviderTypeIdAndOptionIdAndStatus(1L, 12L, "ACTIVE"))
                .thenReturn(List.of(rule(2, new long[]{45,47}, "CAR", "ELECTRIC")));
        var result = resolver.resolve(1L, option, fields,
                List.of(filter(45, "[\"CAR\"]"), filter(47, "[\"ELECTRIC\",\"PETROL\"]")));
        assertThat(result.source()).isEqualTo("OPTION");
    }

    @Test void staleUnknownChoiceCannotResolveACombination() throws Exception {
        var option = option(); option.getMedia().add(media("MAP_MARKER", "ride.png"));
        var fields = List.of(field(45, "[\"CAR\"]"), field(47, "[\"PETROL\"]"));
        when(rules.findAllByServiceProviderTypeIdAndOptionIdAndStatus(1L, 12L, "ACTIVE"))
                .thenReturn(List.of(rule(2, new long[]{45,47}, "CAR", "ELECTRIC")));
        assertThat(resolver.resolve(1L, option, fields,
                List.of(filter(45, "[\"CAR\"]"), filter(47, "[\"ELECTRIC\"]"))).source()).isEqualTo("OPTION");
    }

    @Test void equallySpecificMatchingCombinationsFailClosed() throws Exception {
        var fields = List.of(field(45, "[\"CAR\"]"), field(47, "[\"ELECTRIC\"]"));
        when(rules.findAllByServiceProviderTypeIdAndOptionIdAndStatus(1L, 12L, "ACTIVE")).thenReturn(List.of(
                rule(1, new long[]{45}, "CAR"), rule(2, new long[]{47}, "ELECTRIC")));
        assertThatThrownBy(() -> resolver.resolve(1L, option(), fields,
                List.of(filter(45, "[\"CAR\"]"), filter(47, "[\"ELECTRIC\"]"))))
                .isInstanceOf(com.jobhub.exception.ConflictException.class);
    }

    @Test void selectedVehicleMarkerOverridesOptionAndIgnoresCardIcon() throws Exception {
        var option = option(); option.getMedia().add(media("MAP_MARKER", "ride-marker.png"));
        var field = field(45, """
                [{"value":"CAR","icon":{"key":"car-card.png","url":"https://example.com/car-card.png"},
                "mapMarkerIcon":{"key":"car-marker.png","url":"https://example.com/car-marker.png"}}]
                """);
        var result = resolver.resolve(1L, option, List.of(field), List.of(filter(45, "[\"CAR\"]")));
        assertThat(result.source()).isEqualTo("CHOICE");
        assertThat(result.fieldId()).isEqualTo(45);
        assertThat(result.choiceValue()).isEqualTo("CAR");
        assertThat(result.icon().key()).isEqualTo("car-marker.png");
        verifyNoInteractions(types);
    }

    @Test void ambiguousOrMissingSelectionFallsBackToOptionMarker() throws Exception {
        var option = option(); option.getMedia().add(media("MAP_MARKER", "ride.png"));
        var fields = List.of(field(45, "[\"CAR\",\"VAN\"]"));
        assertThat(resolver.resolve(1L, option, fields, List.of(filter(45, "[\"CAR\",\"VAN\"]"))).source())
                .isEqualTo("OPTION");
        assertThat(resolver.resolve(1L, option, fields, null).icon().key()).isEqualTo("ride.png");
    }

    @Test void choicesWithOnlyCardIconsFallBackToMainServiceMarker() throws Exception {
        var type = new ServiceProviderType(); type.getMedia().add(media("MAP_MARKER", "caregiver-pin.png"));
        when(types.findById(1L)).thenReturn(Optional.of(type));
        var field = field(45, "[{\"value\":\"CAR\",\"icon\":{\"key\":\"car.png\",\"url\":\"https://example.com/car.png\"}}]");
        var result = resolver.resolve(1L, option(), List.of(field), List.of(filter(45, "[\"CAR\"]")));
        assertThat(result.source()).isEqualTo("SERVICE");
        assertThat(result.icon().key()).isEqualTo("caregiver-pin.png");
    }

    @Test void noMarkerConfiguredReturnsBuiltInDefaultWithoutUsingDisplayIcons() {
        var option = option(); option.getMedia().add(media("ICON", "ride-card.png"));
        var type = new ServiceProviderType(); type.getMedia().add(media("ICON", "caregiver-card.png"));
        when(types.findById(1L)).thenReturn(Optional.of(type));
        var result = resolver.resolve(1L, option, List.of(), null);
        assertThat(result.source()).isEqualTo("DEFAULT");
        assertThat(result.icon()).isNull();
    }

    @Test void unrelatedOrPrivateChoiceFieldCannotSupplyMarker() throws Exception {
        var option = option(); option.getMedia().add(media("MAP_MARKER", "ride.png"));
        var unrelated = field(46, "[{\"value\":\"MALE\",\"mapMarkerIcon\":{\"key\":\"male.png\",\"url\":\"https://example.com/male.png\"}}]");
        assertThat(resolver.resolve(1L, option, List.of(unrelated), List.of(filter(46, "[\"MALE\"]"))).source()).isEqualTo("OPTION");
        var privateField = mapper.readValue("""
                {"fieldId":45,"scope":"PROVIDER_REQUIREMENT","status":"ACTIVE","fieldType":"SELECT",
                 "customerVisible":false,"searchable":false,"options":[]}
                """, ServiceCustomFieldResponse.class);
        assertThat(resolver.resolve(1L, option, List.of(privateField), List.of(filter(45, "[\"CAR\"]"))).source()).isEqualTo("OPTION");
    }

    @Test void sharedOptionUsesEachServiceOwnMarkerFieldAndFallback() throws Exception {
        var option = option(); option.getServiceTypes().add(new ServiceTypeOption(2L, 0, "ACTIVE"));
        option.serviceLink(2L).setMapMarkerFieldId(55L);
        var field = field(55, "[{\"value\":\"BIKE\",\"mapMarkerIcon\":{\"key\":\"bike.png\",\"url\":\"https://example.com/bike.png\"}}]");
        var filters = List.of(filter(55, "[\"BIKE\"]"));
        assertThat(resolver.resolve(2L, option, List.of(field), filters).source()).isEqualTo("CHOICE");
        assertThat(resolver.resolve(1L, option, List.of(field), filters).source()).isEqualTo("DEFAULT");
    }
}
