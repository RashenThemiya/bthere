package com.jobhub.service.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Service
public class AwsSesEmailService {

    private final SesV2Client sesClient;
    private final String fromEmail;

    public AwsSesEmailService(
            SesV2Client sesClient,
            @Value("${app.security.email.from}") String fromEmail
    ) {
        if (!StringUtils.hasText(fromEmail)) {
            throw new IllegalStateException("AWS_SES_FROM_EMAIL must be configured");
        }
        this.sesClient = sesClient;
        this.fromEmail = fromEmail;
    }

    public void sendVerificationOtp(String email, String otp, long expirySeconds) {
        long expiryMinutes = Math.max(1, expirySeconds / 60);
        String text = "Your JobHub email verification code is " + otp
                + ". It expires in " + expiryMinutes + " minutes. Do not share this code.";

        Message message = Message.builder()
                .subject(Content.builder().data("Verify your JobHub email").build())
                .body(Body.builder()
                        .text(Content.builder().data(text).build())
                        .build())
                .build();

        sesClient.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(fromEmail)
                .destination(Destination.builder().toAddresses(email).build())
                .content(EmailContent.builder().simple(message).build())
                .build());
    }
}
