package com.jobhub.entity.job;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/** Live-tracking window of one provider on one job. Whether it is still open is derived from the job status. */
@Entity
@Table(name = "job_provider_tracking", uniqueConstraints = @UniqueConstraint(
        name = "uk_job_provider_tracking", columnNames = {"job_id", "service_provider_id"}),
        indexes = @Index(name = "idx_job_provider_tracking_provider", columnList = "service_provider_id"))
@Getter @Setter @NoArgsConstructor
public class JobProviderTracking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long trackingId;
    @Column(nullable = false) private Long jobId;
    @Column(nullable = false) private Long serviceProviderId;
    /** AUTO when the booking was confirmed for "now", MANUAL when the provider pressed Start journey. */
    @Column(nullable = false, length = 10) private String startMode;
    @Column(nullable = false) private Instant journeyStartedAt;
    private Instant arrivedAt;
}
