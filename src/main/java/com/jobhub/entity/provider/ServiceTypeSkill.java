package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "service_type_skill",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_service_type_skill_name",
                columnNames = {"service_provider_type_id", "name"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ServiceTypeSkill extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long skillId;

    private Long serviceProviderTypeId;

    private String name;

    private boolean mandatory;

    private boolean requiresApproval;

    private String status;
}
