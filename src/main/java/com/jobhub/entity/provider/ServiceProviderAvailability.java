package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(name = "service_provider_availability")
@Getter
@Setter
@NoArgsConstructor
public class ServiceProviderAvailability extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long availabilityId;
    private Long serviceProviderId;
    private Long assignmentId;
    private String scheduleType;
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;
    private LocalDate availableDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean allDay;
    @Column(length = 500)
    private String reason;
    private String status;
}

