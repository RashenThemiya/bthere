package com.jobhub.entity.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_communication_preference")
@Getter
@Setter
@NoArgsConstructor
public class UserCommunicationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long preferenceId;
    private Long userId;
    private boolean transactionalEmail;
    private boolean transactionalSms;
    private boolean transactionalPush;
    private boolean marketingEmail;
    private boolean marketingSms;
    private boolean marketingPush;
    @Column(insertable=false, updatable=false)
    private LocalDateTime updatedAt;
}

