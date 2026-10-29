package com.jobhub.entity.job;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "booking_request_policy") @Getter @Setter @NoArgsConstructor
public class BookingRequestPolicy {
    @Id private Long optionId;
    private boolean enabled;
    private int minimumNoticeMinutes = 120;
    private int offerTimeoutSeconds = 60;
    @Column(nullable=false) private int offerGroupSize = 20;
    private int confirmationTimeoutSeconds = 900;
    private int matchingTimeoutSeconds = 600;
    private BigDecimal maximumRadiusKm = new BigDecimal("50");
}
