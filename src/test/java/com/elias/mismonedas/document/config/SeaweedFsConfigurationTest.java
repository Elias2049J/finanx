package com.elias.mismonedas.document.config;

import com.elias.mismonedas.document.adapter.storage.SeaweedFsObjectStorageAdapter;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SeaweedFsConfigurationTest {

    @Test
    void shouldPresignPublicUrlWithConfiguredPort() {
        SeaweedFsProperties properties = new SeaweedFsProperties(
                URI.create("http://seaweedfs:8333"),
                URI.create("http://localhost:8081"),
                "finanx-images",
                "access",
                "secret",
                Duration.ofMinutes(5));
        try (S3Presigner presigner = new SeaweedFsConfiguration().seaweedFsS3Presigner(properties)) {
            SeaweedFsObjectStorageAdapter adapter = new SeaweedFsObjectStorageAdapter(mock(S3Client.class), presigner);

            URI signedUrl = adapter.createPresignedGetUrl("finanx-images", "images/1/receipt.jpg", Duration.ofMinutes(5));

            assertThat(signedUrl.getAuthority()).isEqualTo("localhost:8081");
            assertThat(signedUrl.getRawQuery()).contains("X-Amz-Signature=");
        }
    }
}
