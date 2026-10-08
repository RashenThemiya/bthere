package com.jobhub.config;

import com.jobhub.entity.auth.SmsGatewaySettings;
import com.jobhub.repository.auth.SmsGatewaySettingsRepository;
import com.jobhub.service.admin.SmsGatewaySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SmsGatewaySettingsBootstrap implements ApplicationRunner {

    private final SmsGatewaySettingsRepository repository;

    @Value("${app.security.otp.textlk-token:}")
    private String apiToken;

    @Value("${app.security.otp.sender-id:CareHub}")
    private String senderId;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.existsById(SmsGatewaySettingsService.SETTINGS_ID)
                || !StringUtils.hasText(apiToken)) {
            return;
        }

        SmsGatewaySettings settings = new SmsGatewaySettings();
        settings.setSettingsId(SmsGatewaySettingsService.SETTINGS_ID);
        settings.setApiToken(apiToken.trim());
        settings.setSenderId(senderId.trim());
        settings.setEnabled(true);
        repository.save(settings);
    }
}
