package com.elias.mismonedas.document.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(SeaweedFsProperties.class)
public class SeaweedFsConfiguration {

    @Bean(destroyMethod = "close")
    S3Client seaweedFsS3Client(SeaweedFsProperties properties) {
        return S3Client.builder()
                .endpointOverride(properties.internalEndpoint())
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(pathStyleConfiguration())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner seaweedFsS3Presigner(SeaweedFsProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(properties.publicEndpoint())
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(pathStyleConfiguration())
                .build();
    }

    private StaticCredentialsProvider credentials(SeaweedFsProperties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
        );
    }

    private S3Configuration pathStyleConfiguration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
    }
}
