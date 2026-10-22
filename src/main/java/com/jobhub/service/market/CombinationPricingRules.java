package com.jobhub.service.market;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jobhub.dto.market.PricingConditionRequest;
import com.jobhub.entity.job.JobRate;
import com.jobhub.entity.job.JobRateCondition;
import com.jobhub.exception.ConflictException;
import com.jobhub.repository.job.JobRateRepository;
import com.jobhub.repository.provider.ServiceCustomFieldRepository;
import com.jobhub.service.provider.DynamicFieldChoices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CombinationPricingRules {
    private final ServiceCustomFieldRepository fields;
    private final JobRateRepository rates;
    private final ObjectMapper mapper;

    public List<PricingConditionRequest> normalize(List<PricingConditionRequest> conditions) {
        if (conditions == null) return List.of();
        if (conditions.size() > 10) throw new IllegalArgumentException("At most 10 pricing fields are allowed");
        Map<Long, PricingConditionRequest> unique = new TreeMap<>();
        for (var condition : conditions) {
            if (condition == null || condition.fieldId() == null || condition.fieldId() < 1
                    || condition.choiceValue() == null || condition.choiceValue().isBlank()
                    || condition.choiceValue().length() > 255
                    || unique.putIfAbsent(condition.fieldId(), condition) != null) {
                throw new IllegalArgumentException("Pricing choices require unique field IDs and one nonempty choice per field");
            }
        }
        return List.copyOf(unique.values());
    }

    public void validateChoices(Long serviceTypeId, Long optionId, List<PricingConditionRequest> selections) {
        if (!selections.isEmpty() && optionId == null) {
            throw new IllegalArgumentException("Conditional pricing requires optionId");
        }
        for (var selection : selections) {
            var field = fields.findByFieldIdAndServiceProviderTypeId(selection.fieldId(), serviceTypeId)
                    .or(() -> fields.findById(selection.fieldId())
                            .filter(f -> !f.effectiveOptionIds().isEmpty() && f.appliesToOptions(Set.of(optionId))))
                    .orElseThrow(() -> new IllegalArgumentException("Pricing field does not belong to this service"));
            if (!"ACTIVE".equals(field.getStatus()) || !"PROVIDER_REQUIREMENT".equals(field.getScope())
                    || field.getFieldType() == null || !Set.of("SELECT", "MULTI_SELECT").contains(field.getFieldType())
                    || !field.isCustomerVisible() || !field.isSearchable()
                    || !field.appliesToOptions(Set.of(optionId))) {
                throw new IllegalArgumentException("Pricing requires an active searchable provider choice field for this option");
            }
            try {
                var condition = field.getConditionJson() == null ? null : mapper.readTree(field.getConditionJson());
                if (condition != null && !condition.isNull()) {
                    throw new IllegalArgumentException("Conditional provider fields cannot determine pricing");
                }
                var choices = field.getOptionsJson() == null ? null : mapper.readTree(field.getOptionsJson());
                if (!DynamicFieldChoices.contains(choices, TextNode.valueOf(selection.choiceValue()))) {
                    throw new IllegalArgumentException("Pricing choice is not configured for this field");
                }
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Pricing field configuration is invalid", exception);
            }
        }
    }

    public List<PricingConditionRequest> conditions(JobRate rate) {
        return rate.getConditions().stream()
                .map(c -> new PricingConditionRequest(c.getFieldId(), c.getChoiceValue()))
                .sorted(Comparator.comparing(PricingConditionRequest::fieldId)).toList();
    }

    public void configure(JobRate rate, List<PricingConditionRequest> requested) {
        var conditions = normalize(requested);
        validateChoices(rate.getServiceProviderTypeId(), rate.getServiceOptionId(), conditions);
        if ("ACTIVE".equals(rate.getStatus()) && !conditions.isEmpty()) {
            for (var other : rates.findAllByMarketIdAndServiceProviderTypeIdAndServiceOptionIdAndStatus(
                    rate.getMarketId(), rate.getServiceProviderTypeId(), rate.getServiceOptionId(), "ACTIVE")) {
                if (rate.getRateId() != null && rate.getRateId().equals(other.getRateId())) continue;
                if (conditions.size() == other.getConditions().size()
                        && compatible(conditions, conditions(other)) && overlapsInTime(rate, other)
                        && sameAreaPriorityOverlaps(rate, other)) {
                    throw new ConflictException("Pricing conditions overlap another equally specific active rate");
                }
            }
        }
        rate.getConditions().clear();
        conditions.forEach(c -> rate.getConditions().add(new JobRateCondition(c.fieldId(), c.choiceValue())));
    }

    public boolean matches(JobRate rate, Map<Long, String> selected) {
        return rate.getConditions().stream().allMatch(c -> c.getChoiceValue().equals(selected.get(c.getFieldId())));
    }

    private boolean compatible(List<PricingConditionRequest> left, List<PricingConditionRequest> right) {
        for (var a : left) for (var b : right) {
            if (a.fieldId().equals(b.fieldId()) && !a.choiceValue().equals(b.choiceValue())) return false;
        }
        return true;
    }

    private boolean overlapsInTime(JobRate a, JobRate b) {
        return !(a.getEffectiveTo() != null && b.getEffectiveFrom() != null
                && a.getEffectiveTo().isBefore(b.getEffectiveFrom()))
                && !(b.getEffectiveTo() != null && a.getEffectiveFrom() != null
                && b.getEffectiveTo().isBefore(a.getEffectiveFrom()));
    }

    private boolean sameAreaPriorityOverlaps(JobRate a, JobRate b) {
        if (a.getAreaRadiusKm() == null || b.getAreaRadiusKm() == null) {
            return a.getAreaRadiusKm() == null && b.getAreaRadiusKm() == null;
        }
        if (a.getAreaRadiusKm().compareTo(b.getAreaRadiusKm()) != 0) return false;
        double lat = Math.toRadians(b.getAreaLatitude().doubleValue() - a.getAreaLatitude().doubleValue());
        double lon = Math.toRadians(b.getAreaLongitude().doubleValue() - a.getAreaLongitude().doubleValue());
        double sin = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(a.getAreaLatitude().doubleValue()))
                * Math.cos(Math.toRadians(b.getAreaLatitude().doubleValue()))
                * Math.sin(lon / 2) * Math.sin(lon / 2);
        double distance = 6371.0088 * 2 * Math.atan2(Math.sqrt(Math.min(1, sin)), Math.sqrt(Math.max(0, 1 - sin)));
        return distance <= a.getAreaRadiusKm().add(b.getAreaRadiusKm()).doubleValue();
    }
}
