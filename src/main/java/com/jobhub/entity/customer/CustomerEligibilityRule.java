package com.jobhub.entity.customer;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customer_eligibility_rule", uniqueConstraints = @UniqueConstraint(columnNames = "target_key"))
@Getter @Setter @NoArgsConstructor
public class CustomerEligibilityRule extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long ruleId;
    @Column(nullable = false) private String targetKey;
    private String scope;
    private Long serviceTypeId;
    private Long optionId;
    private Integer minimumAge;
    private String dateOfBirthCode;
    private boolean requiresVerifiedDateOfBirth;
    private boolean enabled;
    @Column(columnDefinition = "TEXT") private String requiredCodesJson;
    @Version private Long rowVersion;
}
