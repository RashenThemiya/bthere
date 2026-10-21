package com.jobhub.entity.provider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter @Setter @NoArgsConstructor
public class ServiceTypeOption {
    @Column(name = "service_provider_type_id", nullable = false)
    private Long serviceProviderTypeId;
    @Column(nullable = false)
    private Integer displayOrder = 0;
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    @Column(name = "map_marker_field_id")
    private Long mapMarkerFieldId;

    public ServiceTypeOption(Long typeId, Integer displayOrder, String status) {
        this.serviceProviderTypeId = typeId;
        this.displayOrder = displayOrder;
        this.status = status;
    }
}
