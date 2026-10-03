package com.jobhub.entity.auth;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "user",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_user_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_user_phone", columnNames = "phone_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;
    private String username;
    private String email;
    private String phoneNumber;
    private String passwordHash;
    private boolean emailVerified;
    private boolean phoneVerified;
    private String status;
    private LocalDateTime lastLoginAt;
}

