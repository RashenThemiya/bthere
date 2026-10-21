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
    private final ServiceCustomFieldService fields;

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
                .filter(f -> Set.of("SELECT", "MULTI_SELECT", "BOOLEAN", "NUMBER").contains(f.fieldType()))
                .filter(f -> f.condition() == null || f.condition().isNull()).toList();
    }

    public List<ProviderFilterDefinition> schema(List<ServiceCustomFieldResponse> definitions) {
        return definitions.stream().filter(ServiceCustomFieldResponse::searchable)
                .map(f -> new ProviderFilterDefinition(f.fieldId(), f.code(), f.label(),
                        f.fieldType(), f.options(), f.filterOperator(), f.icon())).toList();
    }

    public void validate(List<ServiceCustomFieldResponse> definitions, List<ProviderFilter> filters) {
        if (filters == null) return;
        if (filters.size() > 50) throw new IllegalArgumentException("Too many provider filters");
        Set<Long> seen = new HashSet<>();
        for (var filter : filters) {
            if (filter == null || filter.fieldId() == null || !seen.add(filter.fieldId())) invalid();
            var field = definitions.stream().filter(f -> f.fieldId().equals(filter.fieldId()) && f.searchable())
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
            definitions.stream().filter(f -> f.fieldId().equals(answer.fieldId()))
                    .filter(f -> validAnswer(f, answer.value()))
                    .findFirst().ifPresent(f -> result.put(f.fieldId(), answer.value()));
        }
        return result;
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
