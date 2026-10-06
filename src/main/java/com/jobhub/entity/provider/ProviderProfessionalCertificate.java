package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "provider_professional_certificate")
@Getter
@Setter
@NoArgsConstructor
public class ProviderProfessionalCertificate extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long certificateId;
    private Long assignmentId;
    private String name;
    private String issuingOrganization;
    private String certificateNumber;
    private LocalDate issuedDate;
    private LocalDate expiryDate;
    private String documentKey;
    private String verificationStatus;
    private Long verifiedBy;
    private LocalDateTime verifiedAt;
    private String rejectionReason;
}
