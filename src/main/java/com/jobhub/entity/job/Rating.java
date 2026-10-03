package com.jobhub.entity.job;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rating")
@Getter
@Setter
@NoArgsConstructor
public class Rating extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ratingId;
    private Long jobId;
    private Long serviceProviderId;
    private Long customerId;
    private Integer rating;
    @Column(columnDefinition = "TEXT")
    private String comment;
}

