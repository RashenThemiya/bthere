package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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
    private boolean availabilityEnabled;
    /** Legacy V1 compatibility flag. New location rules are configured per option. */
    private boolean serviceAreasEnabled;
    private boolean optionsRequired;
    private Integer minimumOptionSelections;
    private Integer maximumOptionSelections;
    private String certificateRequirement;
    private boolean certificateRequiresApproval;
    private String educationRequirement;
    private boolean educationRequiresApproval;

    @ElementCollection
    @CollectionTable(name = "service_type_media",
            joinColumns = @JoinColumn(name = "service_provider_type_id"))
    @OrderBy("displayOrder ASC")
    private List<ServiceMediaValue> media = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "service_type_responsibility",
            joinColumns = @JoinColumn(name = "service_provider_type_id"))
    @OrderBy("displayOrder ASC")
    private List<ServiceResponsibilityValue> responsibilities = new ArrayList<>();
}

