package com.nirvaankar.marketplace.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

    /**
     * Returns {@code null} when keys are absent so tests and local runs without
     * AWS still start. Catalog listing continues; upload/stream then fail clearly.
     */
    @Bean
    public S3Client s3Client(S3Properties properties) {
        if (!properties.configured()) {
            return null;
        }
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .build();
    }
}
