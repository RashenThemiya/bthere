package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_provider_type")
@Getter
@Setter
@NoArgsConstructor
public class ServiceProviderType extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long serviceProviderTypeId;
    private String name;
    private String description;
    private String iconUrl;
    private String status;
}

