package com.jobhub.service.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jobhub.entity.auth.SmsGatewaySettings;
import com.jobhub.service.admin.SmsGatewaySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TextLkSmsService {

    private final SmsGatewaySettingsService settingsService;
    private final RestClient.Builder restClientBuilder;

    @Value("${app.security.otp.textlk-endpoint:https://app.text.lk/api/v3/sms/send}")
    private String endpoint;

    public void sendOtp(String phoneNumber, String otp, long expirySeconds) {
        SmsGatewaySettings settings = settingsService.requireEnabledSettings();
        long expiryMinutes = Math.max(1, (expirySeconds + 59) / 60);
        String message = "Your CareHub verification code is " + otp
                + ". It expires in " + expiryMinutes + " minutes. Do not share this code.";

        TextLkResponse response = restClientBuilder.build()
                .post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + settings.getApiToken())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(new TextLkRequest(
                        normalizeRecipient(phoneNumber),
                        settings.getSenderId(),
                        "plain",
                        message
                ))
                .retrieve()
                .body(TextLkResponse.class);

        if (response == null || !"success".equalsIgnoreCase(response.status())) {
            String detail = response == null ? "empty response" : response.message();
            throw new IllegalStateException("Text.lk rejected the OTP message: " + detail);
        }
    }

    private String normalizeRecipient(String phoneNumber) {
        String normalized = phoneNumber.trim().replace(" ", "");
        return normalized.startsWith("+") ? normalized.substring(1) : normalized;
    }

    private record TextLkRequest(String recipient, String sender_id, String type, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TextLkResponse(String status, String message) {
    }
}
