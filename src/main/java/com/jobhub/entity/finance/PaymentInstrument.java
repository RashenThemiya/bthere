package com.jobhub.entity.finance;
import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor
@Table(name="payment_instrument",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","provider","type"}))
public class PaymentInstrument extends CreatedEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long methodId;
    private Long userId;
    private String provider;
    private String type;
    private String displayLabel;
    @Column(length=20) private String brand;
    private String status;
}
