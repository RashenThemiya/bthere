package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "service_option", uniqueConstraints = @UniqueConstraint(
        name = "uk_service_option_code", columnNames = {"service_provider_type_id", "code"}))
@Getter @Setter @NoArgsConstructor
public class ServiceOption extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long optionId;
    // Original service retained for database compatibility; membership uses serviceTypes.
    private Long serviceProviderTypeId;

    @ElementCollection
    @CollectionTable(name = "service_type_option", joinColumns = @JoinColumn(name = "option_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_service_type_option",
                    columnNames = {"service_provider_type_id", "option_id"}))
    private Set<ServiceTypeOption> serviceTypes = new LinkedHashSet<>();

    public ServiceTypeOption serviceLink(Long typeId) {
        return serviceTypes.stream().filter(link -> typeId.equals(link.getServiceProviderTypeId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Option is not linked to this service"));
    }

    public boolean activeForService(Long typeId) {
        return "ACTIVE".equals(status) && serviceTypes.stream().anyMatch(link ->
                typeId.equals(link.getServiceProviderTypeId()) && "ACTIVE".equals(link.getStatus()));
    }

    private String code;
    private String name;
    @Column(length = 1000)
    private String description;
    private Integer displayOrder;
    private String status;
    // Shared by every service link; link rows retain only presentation order/status.
    private Long mapMarkerFieldId;
    private String schedulingModel = "TIME_BASED";
    private Integer routeAverageSpeedKmh;
    private boolean locationEnabled;
    private String fulfillmentModel = "ONE_TO_ONE";
    private Integer defaultCapacity = 1;
    private Integer requiredProviderCount = 1;
    private String bookingMode = "MANY_AT_A_TIME";
    @Column(nullable = false, length = 30)
    private String providerSelectionMode = "OPEN_REQUEST";
    @Column(nullable = false, length = 20)
    private String pricingOwner = "ADMIN";
    @Column(precision=5,scale=2)
    private java.math.BigDecimal platformFeePercentage;

    @ElementCollection
    @CollectionTable(name = "service_option_payment_method",
            joinColumns = @JoinColumn(name = "option_id"))
    @Column(name = "payment_method", nullable = false, length = 30)
    private Set<String> allowedPaymentMethods = new LinkedHashSet<>(Set.of(
            "CASH", "CARD", "BANK_TRANSFER", "WALLET", "EZ_CASH", "KOKO"));

    @ElementCollection
    @CollectionTable(name = "service_option_delivery_mode",
            joinColumns = @JoinColumn(name = "option_id"))
    @Column(name = "delivery_mode", nullable = false, length = 40)
    private Set<String> deliveryModes = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_option_media",
            joinColumns = @JoinColumn(name = "option_id"))
    @OrderBy("displayOrder ASC")
    private List<ServiceMediaValue> media = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_option_responsibility",
            joinColumns = @JoinColumn(name = "option_id"))
    @OrderBy("displayOrder ASC")
    private List<ServiceResponsibilityValue> responsibilities = new ArrayList<>();
}
