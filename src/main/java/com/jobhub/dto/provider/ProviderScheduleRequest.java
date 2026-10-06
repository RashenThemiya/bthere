package com.jobhub.dto.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record ProviderScheduleRequest(
        boolean availableAnyDay,
        boolean availableAnyTime,
        @NotNull List<@Valid WeeklySlot> weeklySlots
) {
    public record WeeklySlot(
            @NotNull DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime
    ) {}
}
