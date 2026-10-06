package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateProviderOptionsRequest(
        @NotNull List<@NotNull Long> optionIds
) {}
