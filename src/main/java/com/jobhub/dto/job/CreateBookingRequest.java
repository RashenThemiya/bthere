package com.jobhub.dto.job;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateBookingRequest(
        @NotNull Long serviceTypeId,
        @NotNull Long optionId,
        Long providerId,
        List<Long> providerIds,
        @NotBlank String deliveryMode,
        @Future LocalDateTime startDatetime,
        LocalDateTime expectedEndDatetime,
        @Future LocalDateTime requestedPickupTime,
        @Valid RoutePoint pickup,
        @Valid RoutePoint destination,
        Long providerLocationId,
        @Valid MeetingPoint meetingPoint,
        @Size(max = 4000) String customerNote,
        List<@Valid Answer> answers
) {
    public record Answer(@NotNull Long fieldId, @NotNull JsonNode value) {}

    public record RoutePoint(
            @NotBlank @Size(max = 500) String address,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude
    ) {}

    public record MeetingPoint(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String district,
            @Size(max = 100) String province,
            @Size(max = 30) String postalCode,
            @Size(max = 100) String country,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @Size(max = 1000) String instructions
    ) {}
}
