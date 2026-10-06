package com.jobhub.dto.provider;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProviderUnavailableDateRequest(
        @NotNull @FutureOrPresent LocalDate date,
        @Size(max = 500) String reason
) {}
