package com.jobhub.entity.market;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "market",
        uniqueConstraints = @UniqueConstraint(name = "uk_market_country", columnNames = "country_code")
)
@Getter
@Setter
@NoArgsConstructor
public class Market extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long marketId;

    private String name;
    private String countryCode;
    private String defaultCurrency;
    private String timezone;
    private String locale;
    private String phoneCountryCode;
    private String status;
}
