package com.jobhub.entity.finance;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "payment_transaction")
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentTransactionId;
    private Long paymentId;
    private String transactionType;
    private BigDecimal amount;
    private String providerReference;
    @Column(columnDefinition = "json")
    private String gatewayResponse;
    private String status;
}

