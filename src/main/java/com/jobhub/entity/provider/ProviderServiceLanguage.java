package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "provider_service_language", uniqueConstraints = @UniqueConstraint(
        name = "uk_provider_assignment_language", columnNames = {"assignment_id", "language_code"}))
@Getter @Setter @NoArgsConstructor
public class ProviderServiceLanguage extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long providerLanguageId;
    private Long assignmentId;
    private String languageCode;
}
