package com.jobhub.entity.job;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "emergency_event")
@Getter
@Setter
@NoArgsConstructor
public class EmergencyEvent extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long emergencyEventId;
    private Long jobId;
    private Long triggeredByUserId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String emergencyType;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String status;
    private Long acknowledgedBy;
    private LocalDateTime resolvedAt;
}

