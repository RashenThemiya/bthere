package com.jobhub.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;

/** Select values stay strings; labels and icons are presentation metadata only. */
public final class DynamicFieldChoices {
    private DynamicFieldChoices() {}

    public static void validateDefinition(JsonNode choices) {
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new IllegalArgumentException("Select fields require a non-empty options array");
        }
        Set<String> values = new HashSet<>();
        for (JsonNode choice : choices) {
            JsonNode value = value(choice);
            if (value == null || !value.isTextual() || value.asText().isBlank()
                    || !values.add(value.asText())) {
                throw new IllegalArgumentException("Choice values must be unique nonempty strings");
            }
            if (choice.isObject()) {
                JsonNode label = choice.get("label");
                if (label != null && !label.isNull()
                        && (!label.isTextual() || label.asText().isBlank())) {
                    throw new IllegalArgumentException("Choice label must be a nonempty string");
                }
                validateIcon(choice.get("icon"));
                validateIcon(choice.get("mapMarkerIcon"));
            }
        }
    }

    public static boolean contains(JsonNode choices, JsonNode selected) {
        if (choices == null || !choices.isArray() || selected == null || !selected.isTextual()) return false;
        for (JsonNode choice : choices) if (selected.equals(value(choice))) return true;
        return false;
    }

    /** Defaults are form suggestions, never submitted answers or reviewed evidence. */
    public static void validateDefault(String fieldType, JsonNode choices, JsonNode defaultValue,
                                       boolean profileBacked) {
        if (defaultValue == null || defaultValue.isNull()) return;
        if (profileBacked) {
            throw new IllegalArgumentException("Profile-backed fields use the profile value and cannot set defaultValue");
        }
        if ("SELECT".equals(fieldType)) {
            if (!contains(choices, defaultValue)) {
                throw new IllegalArgumentException("SELECT defaultValue must be a configured choice string");
            }
        } else if ("MULTI_SELECT".equals(fieldType)) {
            if (!defaultValue.isArray()) {
                throw new IllegalArgumentException("MULTI_SELECT defaultValue must be an array of choice strings");
            }
            Set<String> selected = new HashSet<>();
            for (JsonNode value : defaultValue) {
                if (!contains(choices, value) || !selected.add(value.asText())) {
                    throw new IllegalArgumentException("MULTI_SELECT defaults must be unique configured choice strings");
                }
            }
        } else {
            throw new IllegalArgumentException("defaultValue is supported only for SELECT and MULTI_SELECT fields");
        }
    }

    public static com.jobhub.dto.provider.ServiceMediaPayload mapMarkerIcon(JsonNode choices, String selected) {
        if (choices == null || !choices.isArray() || selected == null) return null;
        for (JsonNode choice : choices) {
            JsonNode value = value(choice);
            if (value == null || !value.isTextual() || !selected.equals(value.asText()) || !choice.isObject()) continue;
            JsonNode marker = choice.get("mapMarkerIcon");
            if (marker == null || marker.isNull()) return null;
            validateIcon(marker);
            return new com.jobhub.dto.provider.ServiceMediaPayload(marker.get("key").asText(),
                    marker.get("url").asText(), marker.path("altText").isTextual()
                    ? marker.get("altText").asText() : null,
                    marker.path("displayOrder").isIntegralNumber() ? marker.get("displayOrder").asInt() : null);
        }
        return null;
    }

    private static JsonNode value(JsonNode choice) {
        return choice.isTextual() ? choice : choice.isObject() ? choice.get("value") : null;
    }

    private static void validateIcon(JsonNode icon) {
        if (icon == null || icon.isNull()) return;
        if (!icon.isObject()) throw new IllegalArgumentException("Choice icon must be a media object");
        requireText(icon.get("key"), "key", 1024, true);
        requireText(icon.get("url"), "url", 2048, true);
        requireText(icon.get("altText"), "altText", 300, false);
        JsonNode order = icon.get("displayOrder");
        if (order != null && !order.isNull() && (!order.isIntegralNumber() || !order.canConvertToInt())) {
            throw new IllegalArgumentException("Choice icon displayOrder must be an integer");
        }
    }

    private static void requireText(JsonNode node, String property, int maximum, boolean required) {
        if ((node == null || node.isNull()) && !required) return;
        if (node == null || !node.isTextual() || node.asText().length() > maximum
                || required && node.asText().isBlank()) {
            throw new IllegalArgumentException("Invalid choice icon " + property);
        }
    }
}
