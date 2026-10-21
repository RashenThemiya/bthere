package com.jobhub.entity.provider;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Getter @Setter @NoArgsConstructor
public class MapMarkerCondition {
    @Column(name = "field_id", nullable = false)
    private Long fieldId;
    @Column(name = "choice_value", nullable = false, length = 255)
    private String choiceValue;

    public MapMarkerCondition(Long fieldId, String choiceValue) {
        this.fieldId = fieldId;
        this.choiceValue = choiceValue;
    }
}
