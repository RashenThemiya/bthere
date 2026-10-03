package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "service_provider")
@Getter
@Setter
@NoArgsConstructor
public class ServiceProvider extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long serviceProviderId;
    private Long userId;
    private String firstName;
    private String lastName;
    private String nicNumber;
    private String passportNumber;
    private LocalDate dateOfBirth;
    private String gender;
    private String profilePhoto;
    @Column(columnDefinition = "TEXT")
    private String bio;
    private String verificationStatus;
    private String availabilityStatus;
    private String status;
    private BigDecimal averageRating;
    private Integer totalRatings;
}

