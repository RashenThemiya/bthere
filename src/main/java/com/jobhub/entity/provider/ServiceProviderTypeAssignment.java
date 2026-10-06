package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(
        name = "service_provider_type_assignment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_provider_type_assignment",
                columnNames = {"service_provider_id", "service_provider_type_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ServiceProviderTypeAssignment extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long assignmentId;
    private Long serviceProviderId;
    private Long serviceProviderTypeId;
    private BigDecimal experienceYears;
    private String status;
    private String providerStatus;
    private String adminStatus;
    private String verificationStatus;
    private boolean availableAnyDay;
    private boolean availableAnyTime;
    private boolean emergencyOverride;
    private String emergencyOverrideReason;
    private Long emergencyOverrideBy;
    private java.time.LocalDateTime emergencyOverrideAt;
    private java.time.LocalDateTime emergencyOverrideExpiresAt;
}

