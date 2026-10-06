package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "provider_custom_field_submission", uniqueConstraints = @UniqueConstraint(
        name = "uk_provider_assignment_custom_field", columnNames = {"assignment_id", "field_id"}))
@Getter @Setter @NoArgsConstructor
public class ProviderCustomFieldSubmission extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long submissionId;
    private Long assignmentId;
    private Long fieldId;
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String valueJson;
    private String verificationStatus;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    @Column(length = 1000)
    private String reviewNote;
}
