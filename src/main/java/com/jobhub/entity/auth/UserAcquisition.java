package com.jobhub.entity.auth;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_acquisition")
@Getter
@Setter
@NoArgsConstructor
public class UserAcquisition extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userAcquisitionId;
    private Long userId;
    private String source;
    private String medium;
    private String campaign;
    private String content;
    private String term;
    private String referralCode;
    @Column(length = 2048)
    private String landingPage;
}

