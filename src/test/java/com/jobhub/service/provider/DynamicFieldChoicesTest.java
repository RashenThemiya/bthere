package com.jobhub.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DynamicFieldChoicesTest {
    private final ObjectMapper mapper = new ObjectMapper();

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
