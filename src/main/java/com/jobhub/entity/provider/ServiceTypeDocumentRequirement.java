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
        name = "service_type_document_requirement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_service_type_document_requirement",
                columnNames = {"service_provider_type_id", "document_type_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ServiceTypeDocumentRequirement extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requirementId;

    private Long serviceProviderTypeId;

    private Long documentTypeId;

    private boolean mandatory;

    private boolean requiresApproval;
}
