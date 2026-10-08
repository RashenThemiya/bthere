package com.jobhub.entity.auth;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sms_gateway_settings")
@Getter
@Setter
@NoArgsConstructor
public class SmsGatewaySettings extends AuditedEntity {

    @Id
    private Long settingsId;

    @Column(name = "api_token", nullable = false, length = 4096)
    private String apiToken;

    @Column(name = "sender_id", nullable = false, length = 11)
    private String senderId;

    @Column(nullable = false)
    private boolean enabled;
}
