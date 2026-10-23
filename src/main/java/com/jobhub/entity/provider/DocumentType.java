package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "document_type")
@Getter
@Setter
@NoArgsConstructor
public class DocumentType extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long documentTypeId;
    private String name;
    private String description;
    /**
     * Legacy database column retained for compatibility. Document requirement
     * rules are configured per service in ServiceTypeDocumentRequirement.
     */
    @Deprecated
    private boolean isRequired;
    private boolean hasExpiry;
    private String status;
    private String issuedDateMode;
    private String expiryDateMode;
    @Column(columnDefinition = "LONGTEXT")
    private String partsJson;

    public java.util.List<com.jobhub.dto.provider.DocumentPartDefinition> effectiveParts() {
        return DocumentParts.definitions(partsJson);
    }

    public String effectiveIssuedDateMode() {
        return issuedDateMode == null ? "OPTIONAL" : issuedDateMode;
    }

    public String effectiveExpiryDateMode() {
        return expiryDateMode == null ? (hasExpiry ? "REQUIRED" : "OPTIONAL") : expiryDateMode;
    }
}

