package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "provider_education_qualification")
@Getter
@Setter
@NoArgsConstructor
public class ProviderEducationQualification extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long educationId;
    private Long assignmentId;
    private String qualificationName;
    private String instituteName;
    private String fieldOfStudy;
    private LocalDate startDate;
    private LocalDate completionDate;
    private String result;
    private String documentKey;
    private String verificationStatus;
    private Long verifiedBy;
    private LocalDateTime verifiedAt;
    private String rejectionReason;
}
