package com.jobhub.dto.provider;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record UpdateProviderLanguagesRequest(
        @NotNull List<@Pattern(regexp = "^(SINHALA|ENGLISH|TAMIL)$",
                message = "Language must be SINHALA, ENGLISH or TAMIL") String> languages
) {}
