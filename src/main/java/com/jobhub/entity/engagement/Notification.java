package com.jobhub.entity.engagement;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;
    private Long userId;
    private String title;
    @Column(columnDefinition = "TEXT")
    private String message;
    private String notificationType;
    private String referenceType;
    private Long referenceId;
    private boolean isRead;
    private LocalDateTime readAt;
}

