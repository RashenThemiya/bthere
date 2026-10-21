package com.jobhub.dto.provider;

import com.jobhub.dto.market.PricingConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record MapMarkerRuleRequest(
        @NotNull @Size(min = 1, max = 10) List<@NotNull @Valid PricingConditionRequest> conditions,
        @NotNull @Valid ServiceMediaPayload mapMarkerIcon,
        @Pattern(regexp = "ACTIVE|INACTIVE") String status
) {}
