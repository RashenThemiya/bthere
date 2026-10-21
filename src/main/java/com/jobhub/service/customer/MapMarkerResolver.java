package com.jobhub.service.customer;

import com.jobhub.dto.customer.CustomerDiscoveryApi.MapMarker;
import com.jobhub.dto.customer.CustomerDiscoveryApi.ProviderFilter;
import com.jobhub.dto.provider.ServiceCustomFieldResponse;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import com.jobhub.repository.provider.ServiceOptionMapMarkerRuleRepository;
import com.jobhub.dto.market.PricingConditionRequest;
import com.jobhub.exception.ConflictException;
import com.jobhub.service.provider.DynamicFieldChoices;
import com.jobhub.service.provider.ServiceContentSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapMarkerResolver {
    private final ServiceProviderTypeRepository serviceTypes;
    private final ServiceOptionMapMarkerRuleRepository markerRules;

    public MapMarker resolve(Long serviceTypeId, ServiceOption option,
            List<ServiceCustomFieldResponse> fields, List<ProviderFilter> filters) {
        Long fieldId = option.serviceLink(serviceTypeId).getMapMarkerFieldId();
        Map<Long, String> selected = new HashMap<>();
        if (filters != null) for (var filter : filters) {
            if (filter.value() == null || !filter.value().isArray() || filter.value().size() != 1
                    || !filter.value().get(0).isTextual()) continue;
            fields.stream().filter(f -> filter.fieldId().equals(f.fieldId())
                    && "ACTIVE".equals(f.status()) && "PROVIDER_REQUIREMENT".equals(f.scope())
                    && f.customerVisible() && f.searchable()
                    && ("SELECT".equals(f.fieldType()) || "MULTI_SELECT".equals(f.fieldType()))
                    && (f.condition() == null || f.condition().isNull())
                    && DynamicFieldChoices.contains(f.options(), filter.value().get(0)))
                    .findFirst().ifPresent(f -> selected.put(filter.fieldId(), filter.value().get(0).asText()));
        }
        if (!selected.isEmpty()) {
            var matching = markerRules.findAllByServiceProviderTypeIdAndOptionIdAndStatus(serviceTypeId, option.getOptionId(), "ACTIVE")
                    .stream().filter(rule -> !rule.getConditions().isEmpty()
                            && rule.getConditions().stream().allMatch(c -> c.getChoiceValue().equals(selected.get(c.getFieldId()))))
                    .sorted(Comparator.comparingInt((com.jobhub.entity.provider.ServiceOptionMapMarkerRule r) -> r.getConditions().size()).reversed())
                    .toList();
            if (!matching.isEmpty()) {
                var rule = matching.get(0);
                if (matching.size() > 1 && rule.getConditions().size() == matching.get(1).getConditions().size()) {
                    throw new ConflictException("Multiple equally specific map marker rules match the selected choices");
                }
                var icon = rule.getMarkerIcon();
                return new MapMarker("COMBINATION", new com.jobhub.dto.provider.ServiceMediaPayload(
                        icon.getObjectKey(), icon.getUrl(), icon.getAltText(), icon.getDisplayOrder()), null, null,
                        rule.getMarkerRuleId(), rule.getConditions().stream()
                                .map(c -> new PricingConditionRequest(c.getFieldId(), c.getChoiceValue()))
                                .sorted(Comparator.comparing(PricingConditionRequest::fieldId)).toList());
            }
        }
        if (fieldId != null && filters != null) {
            var field = fields.stream().filter(f -> fieldId.equals(f.fieldId())
                    && "ACTIVE".equals(f.status()) && "PROVIDER_REQUIREMENT".equals(f.scope())
                    && f.customerVisible() && f.searchable()
                    && ("SELECT".equals(f.fieldType()) || "MULTI_SELECT".equals(f.fieldType()))
                    && (f.condition() == null || f.condition().isNull())).findFirst();
            var filter = filters.stream().filter(f -> fieldId.equals(f.fieldId())
                    && f.value() != null && f.value().isArray() && f.value().size() == 1
                    && f.value().get(0).isTextual()).findFirst();
            if (field.isPresent() && filter.isPresent()) {
                String selectedChoice = filter.get().value().get(0).asText();
                var icon = DynamicFieldChoices.mapMarkerIcon(field.get().options(), selectedChoice);
                if (icon != null) return new MapMarker("CHOICE", icon, fieldId, selectedChoice);
            }
        }
        var optionIcon = ServiceContentSupport.mapMarkerIcon(option.getMedia());
        if (optionIcon != null) return new MapMarker("OPTION", optionIcon, null, null);
        var serviceIcon = serviceTypes.findById(serviceTypeId)
                .map(type -> ServiceContentSupport.mapMarkerIcon(type.getMedia())).orElse(null);
        if (serviceIcon != null) return new MapMarker("SERVICE", serviceIcon, null, null);
        return new MapMarker("DEFAULT", null, null, null);
    }
}
