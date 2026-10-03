package com.jobhub.entity.auth;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "login_history")
@Getter
@Setter
@NoArgsConstructor
public class LoginHistory extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long loginHistoryId;
    private Long userId;
    private String provider;
    private String ipAddress;
    @Column(length = 1024)
    private String userAgent;
    private String status;
    private String failureReason;
}

