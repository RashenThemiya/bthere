package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "provider_service_option", uniqueConstraints = @UniqueConstraint(
        name = "uk_provider_assignment_option", columnNames = {"assignment_id", "option_id"}))
@Getter @Setter @NoArgsConstructor
public class ProviderServiceOption extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long providerOptionId;
    private Long assignmentId;
    private Long optionId;
    private String providerStatus;
    private String adminStatus;
    private String verificationStatus;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    @Column(length = 500)
    private String reviewNote;
}
