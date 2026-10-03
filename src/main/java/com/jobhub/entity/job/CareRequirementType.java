package com.jobhub.entity.job;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "care_requirement_type")
@Getter
@Setter
@NoArgsConstructor
public class CareRequirementType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requirementTypeId;
    private String name;
    private String description;
    private String status;
}

