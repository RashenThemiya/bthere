package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_provider_verification")
@Getter
@Setter
@NoArgsConstructor
public class ServiceProviderVerification extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long verificationId;
    private Long serviceProviderId;
    private String verificationType;
    private String status;
    private Long verifiedBy;
    private LocalDateTime verifiedAt;
    private LocalDateTime expiresAt;
    @Column(columnDefinition = "TEXT")
    private String notes;
}

