package com.jobhub.entity.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class ServiceResponsibilityValue {
    @Column(name = "responsible_party", nullable = false, length = 20)
    private String responsibleParty;
    @Column(name = "title", nullable = false, length = 150)
    private String title;
    @Column(name = "description", length = 1000)
    private String description;
    @Column(name = "is_required", nullable = false)
    private boolean required;
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
