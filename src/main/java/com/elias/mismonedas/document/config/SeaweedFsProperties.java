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
        internalEndpoint = requiredUri("internal-endpoint", internalEndpoint);
        publicEndpoint = requiredUri("public-endpoint", publicEndpoint);
        bucket = requiredText("bucket", bucket);
        accessKey = requiredText("access-key", accessKey);
        secretKey = requiredText("secret-key", secretKey);
        if (presignedUrlTtl == null || presignedUrlTtl.isNegative() || presignedUrlTtl.isZero()) {
            throw new IllegalArgumentException("app.storage.seaweedfs.presigned-url-ttl must be positive");
        }
    }

    private static URI requiredUri(String name, URI value) {
        if (value == null) {
            throw new IllegalArgumentException("app.storage.seaweedfs." + name + " is required");
        }
        return value;
    }

    private static String requiredText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("app.storage.seaweedfs." + name + " is required");
        }
        return value;
    }
}
