package com.jobhub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
public class AwsSnsConfig {

    @Bean
    public SnsClient snsClient(
            @Value("${app.security.otp.aws-region}") String region
    ) {
        return SnsClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public SesV2Client sesV2Client(
            @Value("${app.security.otp.aws-region}") String region
    ) {
        return SesV2Client.builder()
                .region(Region.of(region))
                .build();
    }
}
