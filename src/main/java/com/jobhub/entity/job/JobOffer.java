package com.jobhub.entity.job;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_offer")
@Getter
@Setter
@NoArgsConstructor
public class JobOffer extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long offerId;
    private Long jobId;
    private Long serviceProviderId;
    private LocalDateTime offeredAt;
    private LocalDateTime expiresAt;
    private LocalDateTime viewedAt;
    private LocalDateTime respondedAt;
    private String status;
}

