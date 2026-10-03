package com.jobhub.entity.auth;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_session")
@Getter
@Setter
@NoArgsConstructor
public class UserSession extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sessionId;
    private Long userId;
    private Long deviceId;
    private String refreshTokenHash;
    private String ipAddress;
    @Column(length = 1024)
    private String userAgent;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
}

