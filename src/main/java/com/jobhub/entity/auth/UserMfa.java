package com.jobhub.entity.auth;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_mfa")
@Getter
@Setter
@NoArgsConstructor
public class UserMfa extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long mfaId;
    private Long userId;
    private String method;
    private byte[] secretEncrypted;
    private boolean enabled;
    private LocalDateTime verifiedAt;
}

