package com.jobhub.service.admin;

import com.jobhub.dto.admin.SmsGatewaySettingsRequest;
import com.jobhub.dto.admin.SmsGatewaySettingsResponse;
import com.jobhub.entity.auth.SmsGatewaySettings;
import com.jobhub.repository.auth.SmsGatewaySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SmsGatewaySettingsService {

    public static final long SETTINGS_ID = 1L;

    private final SmsGatewaySettingsRepository repository;

    @Value("${app.security.otp.textlk-token:}")
    private String defaultApiToken;

    @Value("${app.security.otp.sender-id:CareHub}")
    private String defaultSenderId;

    @Transactional(readOnly = true)
    public SmsGatewaySettingsResponse get() {
        return toResponse(repository.findById(SETTINGS_ID).orElseGet(this::defaultSettings));
    }

    @Transactional
    public SmsGatewaySettingsResponse update(SmsGatewaySettingsRequest request) {
        SmsGatewaySettings settings = repository.findById(SETTINGS_ID)
                .orElseGet(this::defaultSettings);

        if (StringUtils.hasText(request.apiToken())) {
            settings.setApiToken(request.apiToken().trim());
        }
        if (!StringUtils.hasText(settings.getApiToken())) {
            throw new IllegalArgumentException("API token is required when no token is configured");
        }

        settings.setSenderId(request.senderId().trim());
        settings.setEnabled(request.enabled());
        return toResponse(repository.saveAndFlush(settings));
    }

    @Transactional(readOnly = true)
    public SmsGatewaySettings requireEnabledSettings() {
        SmsGatewaySettings settings = repository.findById(SETTINGS_ID)
                .orElseGet(this::defaultSettings);
        if (!settings.isEnabled()) {
            throw new IllegalStateException("SMS OTP delivery is disabled");
        }
        if (!StringUtils.hasText(settings.getApiToken())) {
            throw new IllegalStateException("Text.lk API token is not configured");
        }
        return settings;
    }

    private SmsGatewaySettings defaultSettings() {
        SmsGatewaySettings settings = new SmsGatewaySettings();
        settings.setSettingsId(SETTINGS_ID);
        settings.setApiToken(defaultApiToken == null ? "" : defaultApiToken.trim());
        settings.setSenderId(defaultSenderId);
        settings.setEnabled(StringUtils.hasText(defaultApiToken));
        return settings;
    }

    private SmsGatewaySettingsResponse toResponse(SmsGatewaySettings settings) {
        String token = settings.getApiToken();
        return new SmsGatewaySettingsResponse(
                "TEXT_LK",
                settings.getSenderId(),
                settings.isEnabled(),
                StringUtils.hasText(token),
                mask(token),
                settings.getUpdatedAt()
        );
    }

    private String mask(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (token.length() <= 8) {
            return "********";
        }
        return token.substring(0, 4) + "********" + token.substring(token.length() - 4);
    }
}
