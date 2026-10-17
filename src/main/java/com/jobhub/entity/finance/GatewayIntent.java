package com.jobhub.entity.finance;
import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name="gateway_intent",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","provider","request_key"}))
public class GatewayIntent extends AuditedEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long intentId;
    private Long userId;
    private String provider;
    @Column(length=64,nullable=false) private String requestKey;
    @Column(length=64,nullable=false) private String requestHash;
    private String purpose;
    private String currencyCode;
    @Column(precision=19,scale=4) private BigDecimal amount;
    private Long jobId;
    private Long methodId;
    private String status;
    @Version private Long version;
}
