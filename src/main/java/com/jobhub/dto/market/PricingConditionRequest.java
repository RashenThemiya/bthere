package com.jobhub.dto.market;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** A single selected value, using the existing provider requirement choice value. */
public record PricingConditionRequest(
        @NotNull @Positive Long fieldId,
        @NotBlank @Size(max = 255) String choiceValue
) {}
