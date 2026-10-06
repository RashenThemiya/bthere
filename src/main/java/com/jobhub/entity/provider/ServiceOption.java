package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "service_option", uniqueConstraints = @UniqueConstraint(
        name = "uk_service_option_code", columnNames = {"service_provider_type_id", "code"}))
@Getter @Setter @NoArgsConstructor
public class ServiceOption extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long optionId;
    private Long serviceProviderTypeId;
    private String code;
    private String name;
    @Column(length = 1000)
    private String description;
    private Integer displayOrder;
    private String status;
    private String schedulingModel = "TIME_BASED";
    private Integer routeAverageSpeedKmh;
    private boolean locationEnabled;
    private String fulfillmentModel = "ONE_TO_ONE";
    private Integer defaultCapacity = 1;
    private Integer requiredProviderCount = 1;
    private String bookingMode = "MANY_AT_A_TIME";

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_option_delivery_mode",
            joinColumns = @JoinColumn(name = "option_id"))
    @Column(name = "delivery_mode", nullable = false, length = 40)
    private Set<String> deliveryModes = new LinkedHashSet<>();
}
