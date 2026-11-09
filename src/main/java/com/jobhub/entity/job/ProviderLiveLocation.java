package com.jobhub.entity.job;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

/** Latest device position of a provider. One row per provider; no path history is kept. */
@Entity @Table(name = "provider_live_location")
@Getter @Setter @NoArgsConstructor
public class ProviderLiveLocation {
    @Id private Long serviceProviderId;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal latitude;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal longitude;
    private Double headingDegrees;
    private Double speedKmh;
    private Double accuracyMeters;
    @Column(nullable = false) private Instant recordedAt;
    @Column(nullable = false) private Instant receivedAt;
}
