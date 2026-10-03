package com.jobhub.entity.provider;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_provider_bank_account")
@Getter
@Setter
@NoArgsConstructor
public class ServiceProviderBankAccount extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bankAccountId;
    private Long serviceProviderId;
    private String bankName;
    private String branchName;
    private String accountHolderName;
    private String accountNumber;
    private boolean isPrimary;
    private String verificationStatus;
}

