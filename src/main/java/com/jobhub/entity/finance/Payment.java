package com.jobhub.entity.finance;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;
    private Long marketId;
    private String currencyCode;
    private Long jobId;
    private Long customerId;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal platformFee;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String paymentProvider;
    private String transactionReference;
    private String paymentStatus;
    private LocalDateTime paidAt;
}

