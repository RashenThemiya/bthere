package com.jobhub.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DynamicFieldChoicesTest {
    @org.junit.jupiter.api.Test
    void mapMarkerIconsAreSeparateValidatedChoiceMetadata() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var choices = mapper.readTree("""
                [{"value":"CAR","icon":{"key":"card.png","url":"https://example.com/card.png"},
                "mapMarkerIcon":{"key":"pin.png","url":"https://example.com/pin.png"}}]
                """);
        assertThatCode(() -> DynamicFieldChoices.validateDefinition(choices)).doesNotThrowAnyException();
        assertThat(DynamicFieldChoices.mapMarkerIcon(choices, "CAR").key()).isEqualTo("pin.png");
        assertThat(DynamicFieldChoices.mapMarkerIcon(choices, "Car")).isNull();
        var invalid = mapper.readTree("[{\"value\":\"CAR\",\"mapMarkerIcon\":{\"key\":\"pin.png\"}}]");
        assertThatThrownBy(() -> DynamicFieldChoices.validateDefinition(invalid)).isInstanceOf(IllegalArgumentException.class);
    }
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void defaultsMustMatchChoiceValuesAndSelectionCardinality() throws Exception {
        var choices = mapper.readTree("[\"BIKE\",{\"value\":\"CAR\",\"label\":\"Car\"}]");
        DynamicFieldChoices.validateDefault("SELECT", choices, mapper.readTree("\"CAR\""), false);
        DynamicFieldChoices.validateDefault("MULTI_SELECT", choices, mapper.readTree("[\"CAR\",\"BIKE\"]"), false);
        DynamicFieldChoices.validateDefault("MULTI_SELECT", choices, mapper.readTree("[]"), false);
        DynamicFieldChoices.validateDefault("TEXT", null, null, false);
        for (String input : new String[]{"\"Car\"", "\"UNKNOWN\"", "[\"CAR\"]", "1", "{\"value\":\"CAR\"}"}) {
            var value = mapper.readTree(input);
            assertThatThrownBy(() -> DynamicFieldChoices.validateDefault("SELECT", choices, value, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String input : new String[]{"\"CAR\"", "[\"CAR\",\"CAR\"]", "[\"UNKNOWN\"]", "[null]", "[1]"}) {
            var value = mapper.readTree(input);
            assertThatThrownBy(() -> DynamicFieldChoices.validateDefault("MULTI_SELECT", choices, value, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var selected = mapper.readTree("\"CAR\"");
        assertThatThrownBy(() -> DynamicFieldChoices.validateDefault("TEXT", choices, selected, false))
                .hasMessageContaining("only for SELECT");
        assertThatThrownBy(() -> DynamicFieldChoices.validateDefault("SELECT", choices, selected, true))
                .hasMessageContaining("Profile-backed");
    }

    @Test void supportsLegacyStringsAndRichChoicesWithOptionalIcons() throws Exception {
        var choices = mapper.readTree("""
                ["CAR", {"value":"VAN","label":"Van","icon":{"key":"service-icons/van.svg",
                 "url":"https://example.com/van.svg"}}, {"value":"BIKE","icon":null}]
                """);
        assertThatCode(() -> DynamicFieldChoices.validateDefinition(choices)).doesNotThrowAnyException();
        assertThat(DynamicFieldChoices.contains(choices, mapper.readTree("\"VAN\""))).isTrue();
        assertThat(DynamicFieldChoices.contains(choices, mapper.readTree("\"Van\""))).isFalse();
        assertThat(DynamicFieldChoices.contains(choices, choices.get(1))).isFalse();
    }

    @Test void rejectsDuplicateValuesAndMalformedPresentationMetadata() throws Exception {
        for (String input : new String[] {
                "[\"CAR\",{\"value\":\"CAR\"}]", "[{\"label\":\"Car\"}]", "[1]",
                "[{\"value\":\"\"}]", "[{\"value\":\"CAR\",\"label\":false}]",
                "[{\"value\":\"CAR\",\"icon\":{\"url\":\"https://example.com/car.png\"}}]",
                "[{\"value\":\"CAR\",\"icon\":{\"key\":\"car.png\",\"url\":\"\"}}]",
                "[{\"value\":\"CAR\",\"icon\":\"car.png\"}]",
                "[{\"value\":\"CAR\",\"icon\":{\"key\":\"car.png\",\"url\":\"https://example.com/car.png\",\"displayOrder\":1.5}}]"
        }) {
            var choices = mapper.readTree(input);
            assertThatThrownBy(() -> DynamicFieldChoices.validateDefinition(choices))
                    .as(input).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
