package com.jobhub.dto.provider;

import com.jobhub.dto.market.PricingConditionRequest;
import java.util.List;

public record MapMarkerRuleResponse(Long ruleId, Long serviceTypeId, Long optionId,
        List<PricingConditionRequest> conditions, ServiceMediaPayload mapMarkerIcon, String status) {}
