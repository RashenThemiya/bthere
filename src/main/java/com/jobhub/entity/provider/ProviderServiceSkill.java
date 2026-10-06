package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "provider_service_skill",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_provider_assignment_skill",
                columnNames = {"assignment_id", "skill_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ProviderServiceSkill extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long providerSkillId;
    private Long assignmentId;
    private Long skillId;
    private String verificationStatus;
    private Long verifiedBy;
    private java.time.LocalDateTime verifiedAt;
    private String rejectionReason;
}
