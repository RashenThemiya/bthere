package com.jobhub.entity.customer;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "patient")
@Getter
@Setter
@NoArgsConstructor
public class Patient extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long patientId;
    private Long customerId;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String mobilityLevel;
    @Column(columnDefinition = "TEXT")
    private String specialNotes;
}

