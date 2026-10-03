package com.jobhub.entity.auth;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_token")
@Getter
@Setter
@NoArgsConstructor
public class VerificationToken extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long verificationId;
    private Long userId;
    private String type;
    private String destination;
    private String tokenHash;
    private Integer attemptCount;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}

