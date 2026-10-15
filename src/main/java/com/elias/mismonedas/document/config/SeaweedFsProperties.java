package com.elias.mismonedas.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.storage.seaweedfs")
public record SeaweedFsProperties(
        URI internalEndpoint,
        URI publicEndpoint,
        String bucket,
        String accessKey,
        String secretKey,
        Duration presignedUrlTtl
) {
    public SeaweedFsProperties {
        if (internalEndpoint == null) internalEndpoint = URI.create("http://localhost:8333");
        if (publicEndpoint == null) publicEndpoint = internalEndpoint;
        if (bucket == null || bucket.isBlank()) bucket = "finanx-images";
        if (accessKey == null || accessKey.isBlank()) accessKey = "finanx-storage";
        if (secretKey == null || secretKey.isBlank()) secretKey = "finanx-storage-dev";
        if (presignedUrlTtl == null || presignedUrlTtl.isNegative() || presignedUrlTtl.isZero()) {
            presignedUrlTtl = Duration.ofMinutes(5);
        }
    }
}
