package com.jobhub.dto.provider;

import java.time.LocalDate;

public record ProviderUnavailableDateResponse(
        Long unavailableDateId,
        LocalDate date,
        String reason
) {}
