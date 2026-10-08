package com.jobhub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AwsConfig {

    @Bean
    public SesV2Client sesV2Client(
            @Value("${app.security.otp.aws-region}") String region
    ) {
        return SesV2Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Client s3Client(
            @Value("${app.storage.s3.region}") String region
    ) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${app.storage.s3.region}") String region
    ) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}
