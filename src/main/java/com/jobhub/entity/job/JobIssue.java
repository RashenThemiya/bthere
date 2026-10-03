package com.jobhub.entity.job;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_issue")
@Getter
@Setter
@NoArgsConstructor
public class JobIssue extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long issueId;
    private Long jobId;
    private Long reportedByUserId;
    private String issueType;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String status;
    private Long assignedAdminId;
    @Column(columnDefinition = "TEXT")
    private String resolution;
    private LocalDateTime resolvedAt;
}

