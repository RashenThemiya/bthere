package com.jobhub.service.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AwsSnsSmsService {

    private final SnsClient snsClient;

    @Value("${app.security.otp.sender-id}")
    private String senderId;

    public void sendOtp(String phoneNumber, String otp, long expirySeconds) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put(
                "AWS.SNS.SMS.SMSType",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("Transactional")
                        .build()
        );

        if (StringUtils.hasText(senderId)) {
            attributes.put(
                    "AWS.SNS.SMS.SenderID",
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(senderId)
                            .build()
            );
        }

        long expiryMinutes = Math.max(1, expirySeconds / 60);
        String message = "Your JobHub verification code is " + otp
                + ". It expires in " + expiryMinutes + " minutes. Do not share this code.";

        snsClient.publish(PublishRequest.builder()
                .phoneNumber(phoneNumber)
                .message(message)
                .messageAttributes(attributes)
                .build());
    }
}
