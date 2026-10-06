package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "service_custom_field", uniqueConstraints = @UniqueConstraint(
        name = "uk_service_custom_field_code",
        columnNames = {"service_provider_type_id", "scope", "option_id", "code"}))
@Getter @Setter @NoArgsConstructor
public class ServiceCustomField extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fieldId;
    private Long serviceProviderTypeId;
    private Long optionId;
    private String scope;
    private String code;
    private String label;
    @Column(length = 1000)
    private String description;
    private String fieldType;
    private boolean required;
    private boolean requiresApproval;
    private Integer minimumFiles;
    private Integer maximumFiles;
    private Integer maximumFileSizeMb;
    @Column(columnDefinition = "TEXT")
    private String allowedFileTypesJson;
    @Column(columnDefinition = "TEXT")
    private String optionsJson;
    @Column(columnDefinition = "TEXT")
    private String conditionJson;
    private Integer displayOrder;
    private Integer version;
    private String status;
}
