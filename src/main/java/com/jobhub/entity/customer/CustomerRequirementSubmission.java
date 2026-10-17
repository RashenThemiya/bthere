package com.jobhub.entity.customer;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(name = "customer_requirement_submission", uniqueConstraints = @UniqueConstraint(
        columnNames = {"customer_id", "requirement_id"}))
@Getter @Setter @NoArgsConstructor
public class CustomerRequirementSubmission extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long submissionId;
    @Column(nullable = false) private Long customerId;
    @Column(nullable = false) private Long requirementId;
    @Column(columnDefinition = "TEXT") private String valueJson;
    private Integer definitionVersion;
    private Integer answerVersion;
    private String status;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    @Column(length = 1000) private String rejectionReason;
    private LocalDate expiresOn;
    @Column(columnDefinition = "TEXT") private String evidenceVersionsJson;
    @Version private Long rowVersion;
}
