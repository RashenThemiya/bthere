package com.jobhub.entity.job;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_attendance")
@Getter
@Setter
@NoArgsConstructor
public class JobAttendance extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attendanceId;
    private Long jobId;
    private Long serviceProviderId;
    private LocalDateTime checkInTime;
    private BigDecimal checkInLatitude;
    private BigDecimal checkInLongitude;
    private LocalDateTime checkOutTime;
    private BigDecimal checkOutLatitude;
    private BigDecimal checkOutLongitude;
    private Integer workedMinutes;
    private String status;
}

