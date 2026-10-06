package com.jobhub.dto.job;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

public record BookingResponse(
        Long jobId,
        Long serviceTypeId,
        Long optionId,
        Long providerId,
        List<Long> providerIds,
        String deliveryMode,
        String schedulingModel,
        String fulfillmentModel,
        Integer capacityUsed,
        Long providerLocationId,
        String meetingPointName,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime startDatetime,
        LocalDateTime expectedEndDatetime,
        BigDecimal destinationLatitude,
        BigDecimal destinationLongitude,
        String destinationAddress,
        BigDecimal estimatedDistanceKm,
        Integer estimatedDurationMinutes,
        Long rateId,
        String currencyCode,
        BigDecimal expectedAmount,
        String status,
        Map<String, JsonNode> answers
) {}
