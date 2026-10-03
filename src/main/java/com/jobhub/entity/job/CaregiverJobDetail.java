package com.jobhub.entity.job;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "caregiver_job_detail")
@Getter
@Setter
@NoArgsConstructor
public class CaregiverJobDetail extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long caregiverJobDetailId;
    private Long jobId;
    private Long patientId;
    private Long hospitalId;
    private String wardNumber;
    private String bedNumber;
    private String careType;
    @Column(columnDefinition = "TEXT")
    private String specialInstruction;
}

