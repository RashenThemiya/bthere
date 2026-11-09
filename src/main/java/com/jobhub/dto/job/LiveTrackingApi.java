package com.jobhub.dto.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobhub.dto.customer.CustomerDiscoveryApi.MapMarker;
import com.jobhub.dto.customer.CustomerDiscoveryApi.ProviderFilterDefinition;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LiveTrackingApi {
    private LiveTrackingApi() {}

    /** Admin option setting. mode: NONE | EN_ROUTE | FULL_TRIP. */
    public record Settings(@NotBlank @Pattern(regexp = "NONE|EN_ROUTE|FULL_TRIP") String mode,
                           @NotNull @Min(0) @Max(1440) Integer autoStartWithinMinutes) {}

    public record LocationUpdate(
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @DecimalMin("0") @DecimalMax("360") Double headingDegrees,
            @DecimalMin("0") @DecimalMax("400") Double speedKmh,
            @DecimalMin("0") @DecimalMax("10000") Double accuracyMeters,
            @NotNull Instant recordedAt) {}

    public record LocationAccepted(Instant receivedAt, List<Long> sharedWithJobIds, int nextUpdateSeconds) {}

    public record Location(BigDecimal latitude, BigDecimal longitude, Double headingDegrees, Double speedKmh,
                           Double accuracyMeters, Instant recordedAt, Instant receivedAt) {}

    /**
     * label: PICKUP | DESTINATION | SERVICE_LOCATION. navigationUrl opens turn-by-turn directions in Google Maps
     * (the app on a phone, the website otherwise).
     */
    public record Target(String label, BigDecimal latitude, BigDecimal longitude, String address, String navigationUrl) {
        public Target(String label, BigDecimal latitude, BigDecimal longitude, String address) {
            this(label, latitude, longitude, address, latitude == null || longitude == null ? null
                    : "https://www.google.com/maps/dir/?api=1&destination=" + latitude.toPlainString() + ","
                    + longitude.toPlainString() + "&travelmode=driving");
        }
    }

    /**
     * status: NOT_SUPPORTED | WAITING_FOR_CONFIRMATION | NOT_STARTED | EN_ROUTE | ARRIVED | IN_TRIP | ENDED.
     * sendLocation tells the provider app whether to stream GPS for this job now.
     */
    public record ProviderState(Long jobId, String jobStatus, String mode, String status, String startMode,
                                boolean canStartJourney, boolean canMarkArrived, boolean sendLocation,
                                int updateIntervalSeconds, Instant journeyStartedAt, Instant arrivedAt, Target target) {}

    public record TrackedProvider(Long providerId, String displayName, String profilePhoto,
                                  BigDecimal averageRating, Integer totalRatings,
                                  Map<Long, JsonNode> providerAttributes, MapMarker mapMarker,
                                  String status, String startMode, Instant journeyStartedAt, Instant arrivedAt,
                                  Location location, boolean stale, Double distanceKm, Integer etaMinutes) {}

    /** status is the overall job tracking status; each provider also carries its own status. */
    public record CustomerTracking(Long jobId, String jobStatus, String mode, String status,
                                   int pollIntervalSeconds, int staleAfterSeconds, Target target,
                                   List<ProviderFilterDefinition> providerDisplayFields,
                                   List<TrackedProvider> providers) {}
}
