package com.jobhub.entity.job;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * A customer's rating of one provider for one completed booking. The booking's service and option are
 * copied in so ratings can be summarised per service option without joining jobs.
 */
@Entity
@Table(name = "rating",
        uniqueConstraints = @UniqueConstraint(name = "uk_rating_job_provider", columnNames = {"job_id", "service_provider_id"}),
        indexes = @Index(name = "idx_rating_provider_option", columnList = "service_provider_id, service_option_id, status"))
@Getter
@Setter
@NoArgsConstructor
public class Rating extends AuditedEntity {
    public static final String VISIBLE = "VISIBLE", HIDDEN = "HIDDEN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ratingId;
    @Column(name = "job_id")
    private Long jobId;
    @Column(name = "service_provider_id")
    private Long serviceProviderId;
    private Long customerId;
    private Long serviceTypeId;
    @Column(name = "service_option_id")
    private Long serviceOptionId;
    private Integer rating;
    @Column(columnDefinition = "TEXT")
    private String comment;
    @Column(length = 20)
    private String status = VISIBLE;
    @Column(length = 500)
    private String moderationReason;
    private Long moderatedBy;
}
