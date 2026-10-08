package com.jobhub.repository.auth;

import com.jobhub.entity.auth.SmsGatewaySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsGatewaySettingsRepository extends JpaRepository<SmsGatewaySettings, Long> {
}
