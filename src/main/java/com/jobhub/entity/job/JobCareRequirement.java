package com.jobhub.entity.job;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "job_care_requirement")
@Getter
@Setter
@NoArgsConstructor
public class JobCareRequirement extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long jobCareRequirementId;
    private Long jobId;
    private Long requirementTypeId;
    private String note;
}

