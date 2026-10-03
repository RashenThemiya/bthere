package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "service_provider_type_assignment")
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
}

