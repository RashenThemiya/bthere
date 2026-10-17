package com.jobhub.entity.customer;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customer_requirement", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter @Setter @NoArgsConstructor
public class CustomerRequirement extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requirementId;
    @Column(nullable = false, length = 100) private String code;
    @Column(nullable = false, length = 200) private String label;
    @Column(length = 1000) private String description;
    private String fieldType;
    private boolean required;
    private String verificationMode;
    private boolean blocksBooking;
    private Integer minimumFiles;
    private Integer maximumFiles;
    private Integer maximumFileSizeMb;
    private Integer displayOrder;
    private Integer definitionVersion;
    private String status;
    @Column(columnDefinition = "TEXT") private String targetsJson;
    @Column(columnDefinition = "TEXT") private String evidenceCodesJson;
    @Column(columnDefinition = "TEXT") private String choicesJson;
    @Column(columnDefinition = "TEXT") private String allowedFileTypesJson;
    @Version private Long rowVersion;
}
