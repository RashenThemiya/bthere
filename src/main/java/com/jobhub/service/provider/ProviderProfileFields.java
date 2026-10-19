package com.jobhub.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.jobhub.dto.provider.ServiceCustomFieldRequest;
import com.jobhub.entity.provider.*;
import com.jobhub.repository.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderProfileFields {
    private static final com.fasterxml.jackson.databind.ObjectMapper CHOICES_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    private final ServiceProviderRepository providers;
    private final ServiceProviderMarketRepository markets;
    private static final Map<String, Set<String>> TYPES = Map.of(
            "firstName", Set.of("TEXT"), "lastName", Set.of("TEXT"),
            "nicNumber", Set.of("TEXT"), "passportNumber", Set.of("TEXT"),
            "dateOfBirth", Set.of("DATE"), "age", Set.of("NUMBER"),
            "gender", Set.of("TEXT", "SELECT"), "profilePhoto", Set.of("TEXT"),
            "bio", Set.of("TEXT", "TEXTAREA"), "marketId", Set.of("NUMBER"));

    public static void validate(ServiceCustomFieldRequest request, String scope, String type) {
        String source = request.valueSource() == null ? "MANUAL" : request.valueSource();
        if (!Set.of("MANUAL", "PROVIDER_PROFILE").contains(source)) {
            throw new IllegalArgumentException("Value source must be MANUAL or PROVIDER_PROFILE");
        }
        if ("MANUAL".equals(source)) {
            if (request.profileField() != null) throw new IllegalArgumentException("Manual fields cannot set profileField");
            return;
        }
        if (!"PROVIDER_REQUIREMENT".equals(scope) || request.profileField() == null
                || !TYPES.getOrDefault(request.profileField(), Set.of()).contains(type)) {
            throw new IllegalArgumentException("Invalid provider profile field or incompatible field type");
        }
        // Profile values are self-declared. Reviewed proof uses separate document/requirement flows.
        if (request.requiresApproval()) {
            throw new IllegalArgumentException("Profile-backed fields cannot require approval; configure reviewed evidence separately");
        }
        if ((request.customerVisible() || request.searchable())
                && !Set.of("gender", "age").contains(request.profileField())) {
            throw new IllegalArgumentException("Only profile gender and derived age can be published as dynamic provider attributes");
        }
    }

    public record Resolved(JsonNode value, String status) {
        public boolean provided() { return "PROVIDED".equals(status); }
    }

    public Resolved resolve(Long providerId, ServiceCustomField field) {
        if (field.isRequiresApproval()) return new Resolved(null, "INVALID");
        ServiceProvider provider = providers.findById(providerId).orElse(null);
        JsonNode value = provider == null ? null : read(provider, field.getProfileField(),
                LocalDate.now(ZoneId.of("Asia/Colombo")));
        if (provider != null && "marketId".equals(field.getProfileField())) {
            value = markets.findByServiceProviderIdAndStatus(providerId, "ACTIVE")
                    .map(m -> m.getMarketId() == null ? null : LongNode.valueOf(m.getMarketId())).orElse(null);
        }
        if (value == null) return new Resolved(null, "MISSING");
        if (!TYPES.getOrDefault(field.getProfileField(), Set.of()).contains(field.getFieldType())) {
            return new Resolved(null, "INVALID");
        }
        if ("SELECT".equals(field.getFieldType())) {
            try {
                var choices = CHOICES_MAPPER.readTree(field.getOptionsJson());
                if (!DynamicFieldChoices.contains(choices, value)) return new Resolved(value, "INVALID");
            } catch (Exception invalid) { return new Resolved(value, "INVALID"); }
        }
        return new Resolved(value, "PROVIDED");
    }

    static JsonNode read(ServiceProvider provider, String field, LocalDate today) {
        if (field == null) return null;
        return switch (field) {
            case "firstName" -> text(provider.getFirstName());
            case "lastName" -> text(provider.getLastName());
            case "nicNumber" -> text(provider.getNicNumber());
            case "passportNumber" -> text(provider.getPassportNumber());
            case "gender" -> text(provider.getGender());
            case "profilePhoto" -> text(provider.getProfilePhoto());
            case "bio" -> text(provider.getBio());
            case "dateOfBirth" -> provider.getDateOfBirth() == null || provider.getDateOfBirth().isAfter(today)
                    ? null : TextNode.valueOf(provider.getDateOfBirth().toString());
            case "age" -> provider.getDateOfBirth() == null || provider.getDateOfBirth().isAfter(today)
                    ? null : IntNode.valueOf(Period.between(provider.getDateOfBirth(), today).getYears());
            default -> null;
        };
    }

    private static JsonNode text(String value) {
        return value == null || value.isBlank() ? null : TextNode.valueOf(value.trim());
    }
}
