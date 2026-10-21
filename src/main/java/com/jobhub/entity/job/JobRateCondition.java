package com.jobhub.entity.job;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor
public class JobRateCondition {
    @Column(name = "field_id", nullable = false)
    private Long fieldId;
    @Column(name = "choice_value", nullable = false, length = 255)
    private String choiceValue;

    public JobRateCondition(Long fieldId, String choiceValue) {
        this.fieldId = fieldId;
        this.choiceValue = choiceValue;
    }
}
