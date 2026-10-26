package com.jobhub.dto.customer;

import com.jobhub.dto.provider.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobhub.dto.customer.CustomerRequirementsApi.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class CustomerDiscoveryApi {
    private CustomerDiscoveryApi() {}
    public record Access(String status, String action, Eligibility eligibility) {}
    public record ServiceCard(ProviderTypeResponse service, Access access) {}
    public record OptionCard(ServiceOptionResponse option, Access access) {}
    public record Context(Long marketId, String timezone, OptionCard selection,
                          ServiceDynamicSchemaResponse bookingSchema, List<String> matchingFilters,
                          boolean liveTrackingSupported, List<ProviderFilterDefinition> providerFilters, List<Long> pricingFieldIds,
                          List<ProviderFilterDefinition> providerDisplayFields) {}
    public record ProviderFilterDefinition(Long fieldId, String code, String label,
                                           String fieldType, JsonNode options, String filterOperator,
                                           ServiceMediaPayload icon) {}
    public record ProviderFilter(@NotNull Long fieldId, JsonNode value, BigDecimal minimum, BigDecimal maximum) {}
    public record Point(@NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {}
    public record Search(
            @NotNull Long marketId, @NotNull Long serviceTypeId, @NotNull Long optionId,
            @NotBlank String deliveryMode, @NotNull OffsetDateTime startAt, OffsetDateTime endAt,
            @Valid Point location, @Valid Point pickup, @Valid Point destination,
            @DecimalMin("0.1") @DecimalMax("500") BigDecimal radiusKm,
            List<@NotNull @Pattern(regexp="SINHALA|ENGLISH|TAMIL") String> languages,
            List<@NotNull Long> requiredSkillIds,
            @Min(0) Integer page, @Min(1) @Max(100) Integer pageSize,
            @Size(max = 50) List<@NotNull @Valid ProviderFilter> providerFilters
    ) {
        public Search(Long marketId, Long serviceTypeId, Long optionId, String deliveryMode,
                      OffsetDateTime startAt, OffsetDateTime endAt, Point location, Point pickup,
                      Point destination, BigDecimal radiusKm, List<String> languages,
                      List<Long> requiredSkillIds, Integer page, Integer pageSize) {
            this(marketId, serviceTypeId, optionId, deliveryMode, startAt, endAt, location,
                    pickup, destination, radiusKm, languages, requiredSkillIds, page, pageSize, null);
        }
    }
    public record Candidate(Long providerId, Long assignmentId, Long optionId, Long providerLocationId,
                            String displayName, String profilePhoto, BigDecimal averageRating,
                            Integer totalRatings, String locationLabel, Point mapPoint,
                            Double distanceKm, int remainingCapacity, List<String> languages,
                            Map<Long, JsonNode> providerAttributes, MapMarker mapMarker) {}
    public record MapMarker(String source, ServiceMediaPayload icon, Long fieldId, String choiceValue,
                            Long ruleId, List<com.jobhub.dto.market.PricingConditionRequest> conditions) {
        public MapMarker(String source, ServiceMediaPayload icon, Long fieldId, String choiceValue) {
            this(source, icon, fieldId, choiceValue, null, List.of());
        }
    }
    public record Results(Instant checkedAt, Long marketId, String timezone, OffsetDateTime startAt,
                          OffsetDateTime endAt, String providerSelectionMode, int requiredProviderCount,
                          String status, int totalCandidates, int page, int pageSize,
                          boolean hasNext, List<Candidate> candidates) {}
}
