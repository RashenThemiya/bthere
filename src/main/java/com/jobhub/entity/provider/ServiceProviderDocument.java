package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(
        name = "service_provider_document",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_provider_document_type",
                columnNames = {"service_provider_id", "document_type_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ServiceProviderDocument extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long documentId;
    private Long serviceProviderId;
    private Long documentTypeId;
    private String documentName;
    private String documentNumber;
    private String documentUrl;
    private LocalDate issuedDate;
    private LocalDate expiryDate;
    private String verificationStatus;
    private Long verifiedBy;
    private LocalDateTime verifiedAt;
    private String rejectionReason;
}

