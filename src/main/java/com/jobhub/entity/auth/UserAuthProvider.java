package com.jobhub.entity.auth;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "user_auth_provider",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_auth_provider_user",
                columnNames = {"provider", "provider_user_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class UserAuthProvider extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long authProviderId;
    private Long userId;
    private String provider;
    private String providerUserId;
    private String providerEmail;
}

