package com.jobhub.entity.job;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_provider_assignment", uniqueConstraints = @UniqueConstraint(
        name = "uk_job_provider_assignment", columnNames = {"job_id", "service_provider_id"}))
@Getter @Setter @NoArgsConstructor
public class JobProviderAssignment extends CreatedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long jobProviderAssignmentId;
    private Long jobId;
    private Long serviceProviderId;
    private String status;
}
