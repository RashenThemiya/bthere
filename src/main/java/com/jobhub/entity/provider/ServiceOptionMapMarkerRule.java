package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Table(name = "service_option_map_marker_rule", indexes = @Index(
        name = "idx_marker_rule_scope", columnList = "service_provider_type_id,option_id,status"))
@Getter @Setter @NoArgsConstructor
public class ServiceOptionMapMarkerRule extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long markerRuleId;
    private Long serviceProviderTypeId;
    private Long optionId;
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    @Embedded
    private ServiceMediaValue markerIcon;
    @ElementCollection
    @CollectionTable(name = "service_option_map_marker_condition",
            joinColumns = @JoinColumn(name = "marker_rule_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_marker_rule_field",
                    columnNames = {"marker_rule_id", "field_id"}))
    private List<MapMarkerCondition> conditions = new ArrayList<>();
}
