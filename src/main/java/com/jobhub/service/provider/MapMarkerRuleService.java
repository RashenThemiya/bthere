package com.jobhub.service.provider;

import com.fasterxml.jackson.databind.node.TextNode;
import com.jobhub.dto.market.PricingConditionRequest;
import com.jobhub.dto.provider.*;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.*;
import com.jobhub.repository.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MapMarkerRuleService {
    private final ServiceOptionRepository options;
    private final ServiceOptionMapMarkerRuleRepository rules;
    private final ServiceCustomFieldService fields;

    @Transactional(readOnly = true)
    public List<MapMarkerRuleResponse> list(Long serviceTypeId, Long optionId) {
        requireOption(serviceTypeId, optionId, false);
        return rules.findAllByServiceProviderTypeIdAndOptionIdOrderByMarkerRuleIdAsc(serviceTypeId, optionId)
                .stream().map(this::response).toList();
    }

    @Transactional
    public MapMarkerRuleResponse create(Long serviceTypeId, Long optionId, MapMarkerRuleRequest request) {
        requireOption(serviceTypeId, optionId, true);
        var rule = new ServiceOptionMapMarkerRule();
        rule.setServiceProviderTypeId(serviceTypeId); rule.setOptionId(optionId);
        apply(rule, request);
        return response(rules.save(rule));
    }

    @Transactional
    public MapMarkerRuleResponse update(Long serviceTypeId, Long optionId, Long ruleId, MapMarkerRuleRequest request) {
        requireOption(serviceTypeId, optionId, true);
        var rule = requireRule(serviceTypeId, optionId, ruleId);
        apply(rule, request);
        return response(rules.save(rule));
    }

    @Transactional
    public void delete(Long serviceTypeId, Long optionId, Long ruleId) {
        requireOption(serviceTypeId, optionId, true);
        rules.delete(requireRule(serviceTypeId, optionId, ruleId));
    }

    private void requireOption(Long serviceTypeId, Long optionId, boolean lock) {
        var option = (lock ? options.findLockedById(optionId)
                : options.findByOptionIdAndServiceProviderTypeId(optionId, serviceTypeId))
                .orElseThrow(() -> new ResourceNotFoundException("Service option not found"));
        if (option.getServiceTypes().stream().noneMatch(l -> serviceTypeId.equals(l.getServiceProviderTypeId()))) {
            throw new ResourceNotFoundException("Service option not found for this service");
        }
    }

    private ServiceOptionMapMarkerRule requireRule(Long serviceTypeId, Long optionId, Long ruleId) {
        return rules.findById(ruleId)
                .filter(r -> serviceTypeId.equals(r.getServiceProviderTypeId()) && optionId.equals(r.getOptionId()))
                .orElseThrow(() -> new ResourceNotFoundException("Map marker rule not found for this service option"));
    }

    private void apply(ServiceOptionMapMarkerRule rule, MapMarkerRuleRequest request) {
        if (request.conditions() == null || request.conditions().isEmpty() || request.conditions().size() > 10) {
            throw new IllegalArgumentException("Map marker rules require between 1 and 10 choice conditions");
        }
        Map<Long, PricingConditionRequest> conditions = new TreeMap<>();
        var definitions = fields.listForOption(rule.getServiceProviderTypeId(), rule.getOptionId(), false);
        for (var choice : request.conditions()) {
            if (choice == null || choice.fieldId() == null || choice.fieldId() < 1
                    || choice.choiceValue() == null || choice.choiceValue().isBlank() || choice.choiceValue().length() > 255
                    || conditions.putIfAbsent(choice.fieldId(), choice) != null) {
                throw new IllegalArgumentException("Map marker conditions require unique field IDs and one choice per field");
            }
            var field = definitions.stream().filter(f -> choice.fieldId().equals(f.fieldId())
                    && "ACTIVE".equals(f.status()) && "PROVIDER_REQUIREMENT".equals(f.scope())
                    && f.customerVisible() && f.searchable()
                    && ("SELECT".equals(f.fieldType()) || "MULTI_SELECT".equals(f.fieldType()))
                    && (f.condition() == null || f.condition().isNull())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Map marker conditions require active searchable provider choice fields for this service option"));
            if (!DynamicFieldChoices.contains(field.options(), TextNode.valueOf(choice.choiceValue()))) {
                throw new IllegalArgumentException("Map marker choice is not configured for this field");
            }
        }
        String status = request.status() == null ? "ACTIVE" : request.status();
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new IllegalArgumentException("Invalid map marker rule status");
        var icon = request.mapMarkerIcon();
        if (icon == null || icon.key() == null || icon.key().isBlank() || icon.key().length() > 1024
                || icon.url() == null || icon.url().isBlank() || icon.url().length() > 2048
                || icon.altText() != null && icon.altText().length() > 300) {
            throw new IllegalArgumentException("Map marker icon requires a valid key and URL");
        }
        if ("ACTIVE".equals(status)) {
            for (var other : rules.findAllByServiceProviderTypeIdAndOptionIdAndStatus(
                    rule.getServiceProviderTypeId(), rule.getOptionId(), "ACTIVE")) {
                if (rule.getMarkerRuleId() != null && rule.getMarkerRuleId().equals(other.getMarkerRuleId())) continue;
                if (other.getConditions().size() == conditions.size()
                        && other.getConditions().stream().allMatch(c -> !conditions.containsKey(c.getFieldId())
                            || c.getChoiceValue().equals(conditions.get(c.getFieldId()).choiceValue()))) {
                    throw new ConflictException("Map marker rule overlaps another equally specific active rule");
                }
            }
        }
        var media = new ServiceMediaValue(); media.setMediaType("MAP_MARKER");
        media.setObjectKey(icon.key().trim()); media.setUrl(icon.url().trim()); media.setAltText(icon.altText());
        media.setDisplayOrder(icon.displayOrder() == null ? 0 : icon.displayOrder());
        rule.setMarkerIcon(media); rule.setStatus(status);
        rule.getConditions().clear();
        conditions.values().forEach(c -> rule.getConditions().add(new MapMarkerCondition(c.fieldId(), c.choiceValue())));
    }

    private MapMarkerRuleResponse response(ServiceOptionMapMarkerRule rule) {
        var icon = rule.getMarkerIcon();
        return new MapMarkerRuleResponse(rule.getMarkerRuleId(), rule.getServiceProviderTypeId(), rule.getOptionId(),
                rule.getConditions().stream().map(c -> new PricingConditionRequest(c.getFieldId(), c.getChoiceValue()))
                        .sorted(Comparator.comparing(PricingConditionRequest::fieldId)).toList(),
                new ServiceMediaPayload(icon.getObjectKey(), icon.getUrl(), icon.getAltText(), icon.getDisplayOrder()), rule.getStatus());
    }
}
