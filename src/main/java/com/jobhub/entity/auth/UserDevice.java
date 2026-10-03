package com.jobhub.entity.auth;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_device")
@Getter
@Setter
@NoArgsConstructor
public class UserDevice extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long deviceId;
    private Long userId;
    private String deviceToken;
    private String platform;
    private String deviceName;
    private String status;
    private LocalDateTime lastActiveAt;
}

