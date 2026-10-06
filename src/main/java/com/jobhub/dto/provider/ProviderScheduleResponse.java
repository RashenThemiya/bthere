package com.jobhub.dto.provider;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record ProviderScheduleResponse(
        boolean availableAnyDay,
        boolean availableAnyTime,
        List<WeeklySlot> weeklySlots
) {
    public record WeeklySlot(
            Long availabilityId,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime
    ) {}
}
