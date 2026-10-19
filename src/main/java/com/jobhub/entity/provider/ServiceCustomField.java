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
    @ElementCollection
    @CollectionTable(name = "service_custom_field_option", joinColumns = @JoinColumn(name = "field_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"field_id", "option_id"}))
    @Column(name = "option_id", nullable = false)
    private java.util.Set<Long> optionIds = new java.util.LinkedHashSet<>();

    public java.util.Set<Long> effectiveOptionIds() {
        if (optionIds != null && !optionIds.isEmpty()) return optionIds;
        return optionId == null ? java.util.Set.of() : java.util.Set.of(optionId);
    }

    public boolean appliesToOptions(java.util.Set<Long> selectedOptions) {
        return effectiveOptionIds().isEmpty()
                || effectiveOptionIds().stream().anyMatch(selectedOptions::contains);
    }
    private String scope;
    private String reuseScope;
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
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean customerVisible;
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean searchable;
    private String filterOperator;
    @Column(columnDefinition = "TEXT")
    private String iconJson;
    private String valueSource;
    private String profileField;

    public String effectiveValueSource() { return valueSource == null ? "MANUAL" : valueSource; }
    public boolean isProfileBacked() { return "PROVIDER_PROFILE".equals(effectiveValueSource()); }
}
