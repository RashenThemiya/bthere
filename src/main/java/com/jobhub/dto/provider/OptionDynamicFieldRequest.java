package com.jobhub.dto.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OptionDynamicFieldRequest(
        @Positive Long fieldId,
        @NotNull @Valid ServiceCustomFieldRequest definition
) {}
