package com.jobhub.entity.audit;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;
    private Long userId;
    private String action;
    private String entityType;
    private Long entityId;
    @Column(columnDefinition = "json")
    private String oldValue;
    @Column(columnDefinition = "json")
    private String newValue;
    private String ipAddress;
}

