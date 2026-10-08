package com.jobhub.service.admin;

import com.jobhub.dto.admin.SmsGatewaySettingsRequest;
import com.jobhub.dto.admin.SmsGatewaySettingsResponse;
import com.jobhub.entity.auth.SmsGatewaySettings;
import com.jobhub.repository.auth.SmsGatewaySettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsGatewaySettingsServiceTest {

    @Mock
    private SmsGatewaySettingsRepository repository;

    @InjectMocks
    private SmsGatewaySettingsService service;

    @Test
    void updateWithoutTokenRetainsStoredTokenAndMasksResponse() {
        SmsGatewaySettings settings = new SmsGatewaySettings();
        settings.setSettingsId(SmsGatewaySettingsService.SETTINGS_ID);
        settings.setApiToken("8106|secret-token-4069b");
        settings.setSenderId("OldSender");
        settings.setEnabled(true);

        when(repository.findById(SmsGatewaySettingsService.SETTINGS_ID))
                .thenReturn(Optional.of(settings));
        when(repository.saveAndFlush(any(SmsGatewaySettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SmsGatewaySettingsResponse response = service.update(
                new SmsGatewaySettingsRequest(null, "CareHub", true)
        );

        assertThat(settings.getApiToken()).isEqualTo("8106|secret-token-4069b");
        assertThat(settings.getSenderId()).isEqualTo("CareHub");
        assertThat(response.maskedApiToken()).isEqualTo("8106********069b");
    }

    @Test
    void environmentDefaultsEnableGatewayWhenTokenExists() {
        ReflectionTestUtils.setField(service, "defaultApiToken", "configured-token");
        ReflectionTestUtils.setField(service, "defaultSenderId", "CareHub");
        when(repository.findById(SmsGatewaySettingsService.SETTINGS_ID))
                .thenReturn(Optional.empty());

        SmsGatewaySettings settings = service.requireEnabledSettings();

        assertThat(settings.isEnabled()).isTrue();
        assertThat(settings.getSenderId()).isEqualTo("CareHub");
    }
}
