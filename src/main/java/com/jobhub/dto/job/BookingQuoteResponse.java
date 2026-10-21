package com.jobhub.dto.job;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Preview only. Availability and price are checked again when the booking is created. */
public record BookingQuoteResponse(Long rateId, String currencyCode, BigDecimal expectedAmount,
        LocalDateTime startDatetime, LocalDateTime expectedEndDatetime, BigDecimal estimatedDistanceKm,
        JsonNode pricingSelections, JsonNode pricingSnapshot) {}
