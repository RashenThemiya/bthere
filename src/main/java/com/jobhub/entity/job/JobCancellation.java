package com.jobhub.entity.job;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "job_cancellation")
@Getter
@Setter
@NoArgsConstructor
public class JobCancellation extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cancellationId;
    private Long jobId;
    private Long cancelledByUserId;
    private String reason;
    @Column(columnDefinition = "TEXT")
    private String note;
    private BigDecimal cancellationFee;
    private BigDecimal refundAmount;
}

