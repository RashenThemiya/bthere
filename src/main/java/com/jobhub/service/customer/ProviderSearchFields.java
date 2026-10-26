package com.jobhub.service.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhub.dto.customer.CustomerDiscoveryApi.*;
import com.jobhub.dto.provider.ServiceCustomFieldResponse;
import com.jobhub.entity.provider.ServiceProviderTypeAssignment;
import com.jobhub.service.provider.ServiceCustomFieldService;
import com.jobhub.service.provider.DynamicFieldChoices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProviderSearchFields {
    private static final Set<String> SEARCH_TYPES = Set.of("SELECT", "MULTI_SELECT", "NUMBER", "BOOLEAN");
    private final ServiceCustomFieldService fields;
    private final com.jobhub.repository.provider.ServiceProviderRepository providers;
    private final com.jobhub.service.upload.S3PrivateDocumentService storage;

    public void requirePricingSelections(ServiceProviderTypeAssignment assignment, Long optionId,
            List<com.jobhub.dto.market.PricingConditionRequest> selections) {
        if (selections == null || selections.isEmpty()) return;
        var definitions = definitions(assignment.getServiceProviderTypeId(), optionId);
        var filters = selections.stream().map(selection -> new ProviderFilter(selection.fieldId(),
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode()
                        .add(selection.choiceValue()), null, null)).toList();
        validate(definitions, filters);
        if (!matches(attributes(assignment, definitions), filters)) {
            throw new IllegalArgumentException("Provider does not offer the selected pricing choices");
        }
    }

    public List<ServiceCustomFieldResponse> definitions(Long serviceId, Long optionId) {
        return fields.listForOption(serviceId, optionId, false).stream()
                .filter(f -> "PROVIDER_REQUIREMENT".equals(f.scope()) && f.customerVisible())
                .filter(f -> f.condition() == null || f.condition().isNull()).toList();
    }

    public List<ProviderFilterDefinition> schema(List<ServiceCustomFieldResponse> definitions) {
        return definitions.stream().filter(f -> f.searchable() && SEARCH_TYPES.contains(f.fieldType()))
                .map(f -> new ProviderFilterDefinition(f.fieldId(), f.code(), f.label(),
                        f.fieldType(), f.options(), f.filterOperator(), f.icon())).toList();
    }

    public List<ProviderFilterDefinition> displaySchema(List<ServiceCustomFieldResponse> definitions) {
        return definitions.stream().map(f -> new ProviderFilterDefinition(f.fieldId(), f.code(), f.label(),
                f.fieldType(), f.options(), f.filterOperator(), f.icon())).toList();
    }

    public void validate(List<ServiceCustomFieldResponse> definitions, List<ProviderFilter> filters) {
        if (filters == null) return;
        if (filters.size() > 50) throw new IllegalArgumentException("Too many provider filters");
        Set<Long> seen = new HashSet<>();
        for (var filter : filters) {
            if (filter == null || filter.fieldId() == null || !seen.add(filter.fieldId())) invalid();
            var field = definitions.stream().filter(f -> f.fieldId().equals(filter.fieldId()) && f.searchable() && SEARCH_TYPES.contains(f.fieldType()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Provider filter is not available for this option"));
            if ("NUMBER".equals(field.fieldType())) {
                if (filter.value() != null || filter.minimum() == null && filter.maximum() == null
                        || filter.minimum() != null && filter.maximum() != null
                        && filter.minimum().compareTo(filter.maximum()) > 0) invalid();
            } else {
                if (filter.minimum() != null || filter.maximum() != null || filter.value() == null) invalid();
                if ("BOOLEAN".equals(field.fieldType())) {
                    if (!filter.value().isBoolean()) invalid();
                } else {
                    if (!filter.value().isArray() || filter.value().isEmpty() || filter.value().size() > 100) invalid();
                    Set<String> values = new HashSet<>();
                    filter.value().forEach(v -> {
                        if (!v.isTextual() || !isChoice(field, v) || !values.add(v.asText())) invalid();
                    });
                }
            }
        }
    }

    public Map<Long, JsonNode> attributes(ServiceProviderTypeAssignment assignment,
                                          List<ServiceCustomFieldResponse> definitions) {
        if (definitions.isEmpty()) return Map.of();
        Map<Long, JsonNode> result = new LinkedHashMap<>();
        for (var answer : fields.resolvedProviderAnswers(assignment)) {
            if (!"APPROVED".equals(answer.verificationStatus())
                    && !("PROVIDER_PROFILE".equals(answer.valueSource()) && "PROVIDED".equals(answer.verificationStatus()))) continue;
            definitions.stream().filter(f -> f.fieldId().equals(answer.fieldId()) && f.customerVisible()
                            && "PROVIDER_REQUIREMENT".equals(f.scope())
                            && (f.condition() == null || f.condition().isNull()))
                    .filter(f -> validAnswer(f, answer.value()))
                    .findFirst().ifPresent(f -> {
                        if (f.fieldType().endsWith("UPLOAD")) {
                            var images = publishedFiles(assignment, f.fieldType(), answer.value());
                            if (!images.isEmpty()) result.put(f.fieldId(), images);
                        } else result.put(f.fieldId(), answer.value());
                    });
        }
        return result;
    }

    private JsonNode publishedFiles(ServiceProviderTypeAssignment assignment, String fieldType, JsonNode value) {
        var images = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        var provider = providers.findById(assignment.getServiceProviderId()).orElse(null);
        if (provider == null || provider.getUserId() == null) return images;
        JsonNode files = value.isArray() ? value : value.path("files");
        for (var file : files) {
            var key = file.isTextual() ? file : file.path("key");
            if (!key.isTextual()) continue;
            try {
                var view = "IMAGE_UPLOAD".equals(fieldType)
                        ? storage.createPublishedProviderImageUrl(provider.getUserId(), key.asText())
                        : storage.createPublishedProviderFileUrl(provider.getUserId(), key.asText());
                var image = images.addObject();
                image.put("key", view.key());
                image.put("url", view.url());
                image.put("expiresAt", view.expiresAt().toString());
            } catch (IllegalArgumentException ignored) {
                // Skip malformed legacy answers, foreign files and deleted/unsupported objects.
            }
        }
        return images;
    }

    public boolean matches(Map<Long, JsonNode> attributes, List<ProviderFilter> filters) {
        if (filters == null) return true;
        for (var filter : filters) {
            JsonNode answer = attributes.get(filter.fieldId());
            if (answer == null) return false;
            if (filter.minimum() != null || filter.maximum() != null) {
                if (!answer.isNumber() || filter.minimum() != null && answer.decimalValue().compareTo(filter.minimum()) < 0
                        || filter.maximum() != null && answer.decimalValue().compareTo(filter.maximum()) > 0) return false;
            } else if (filter.value().isBoolean()) {
                if (!filter.value().equals(answer)) return false;
            } else {
                boolean found = false;
                for (JsonNode choice : filter.value()) {
                    if (answer.isArray()) {
                        for (JsonNode item : answer) if (choice.equals(item)) found = true;
                    } else if (choice.equals(answer)) found = true;
                }
                if (!found) return false;
            }
        }
        return true;
    }

    private boolean validAnswer(ServiceCustomFieldResponse field, JsonNode value) {
        if (value == null || value.isNull()) return false;
        return switch (field.fieldType()) {
            case "IMAGE_UPLOAD", "DOCUMENT_UPLOAD", "FILE_UPLOAD" -> value.isArray() || value.path("files").isArray();
            case "TEXT", "TEXTAREA", "DATE", "TIME", "PHONE", "EMAIL" -> value.isTextual();
            case "NUMBER" -> value.isNumber();
            case "BOOLEAN" -> value.isBoolean();
            case "SELECT" -> value.isTextual() && isChoice(field, value);
            case "MULTI_SELECT" -> {
                boolean valid = value.isArray() && !value.isEmpty();
                if (valid) for (var item : value) valid &= item.isTextual() && isChoice(field, item);
                yield valid;
            }
            default -> false;
        };
    }

    private boolean isChoice(ServiceCustomFieldResponse field, JsonNode value) {
        return DynamicFieldChoices.contains(field.options(), value);
    }

    private void invalid() { throw new IllegalArgumentException("Invalid provider filter value or range"); }
}
