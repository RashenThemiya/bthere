package com.jobhub.dto.customer;

import com.jobhub.dto.provider.*;
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
                          boolean liveTrackingSupported) {}
    public record Point(@NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {}
    public record Search(
            @NotNull Long marketId, @NotNull Long serviceTypeId, @NotNull Long optionId,
            @NotBlank String deliveryMode, @NotNull OffsetDateTime startAt, OffsetDateTime endAt,
            @Valid Point location, @Valid Point pickup, @Valid Point destination,
            @DecimalMin("0.1") @DecimalMax("500") BigDecimal radiusKm,
            List<@NotNull @Pattern(regexp="SINHALA|ENGLISH|TAMIL") String> languages,
            List<@NotNull Long> requiredSkillIds,
            @Min(0) Integer page, @Min(1) @Max(100) Integer pageSize
    ) {}
    public record Candidate(Long providerId, Long assignmentId, Long optionId, Long providerLocationId,
                            String displayName, String profilePhoto, BigDecimal averageRating,
                            Integer totalRatings, String locationLabel, Point mapPoint,
                            Double distanceKm, int remainingCapacity, List<String> languages) {}
    public record Results(Instant checkedAt, Long marketId, String timezone, OffsetDateTime startAt,
                          OffsetDateTime endAt, String providerSelectionMode, int requiredProviderCount,
                          String status, int totalCandidates, int page, int pageSize,
                          boolean hasNext, List<Candidate> candidates) {}
}
