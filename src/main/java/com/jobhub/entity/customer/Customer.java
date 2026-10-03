package com.jobhub.entity.customer;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long customerId;
    private Long userId;
    private String firstName;
    private String lastName;
    private String profilePhoto;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String district;
    private String province;
    private String postalCode;
    private String country;
    private String status;
}

