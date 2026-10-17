package com.jobhub.entity.finance;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="payment_gateway_settings") @Getter @Setter
public class PaymentGatewaySettings {
 @Id private Long settingsId=1L;
 @Column(nullable=false,length=16) private String provider="DISABLED";
 @Version private Long version;
}
