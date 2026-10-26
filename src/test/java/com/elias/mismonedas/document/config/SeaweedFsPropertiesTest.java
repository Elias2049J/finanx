package com.elias.mismonedas.document.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeaweedFsPropertiesTest {

    @Test
    void shouldAcceptCompleteConfiguration() {
        SeaweedFsProperties properties = new SeaweedFsProperties(
                URI.create("http://localhost:8333"),
                URI.create("http://localhost:8081"),
                "finanx-images",
                "access-key",
                "secret-key",
                Duration.ofMinutes(5)
        );

        assertThat(properties.bucket()).isEqualTo("finanx-images");
        assertThat(properties.presignedUrlTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void shouldRejectMissingCredentials() {
        assertThatThrownBy(() -> new SeaweedFsProperties(
                URI.create("http://localhost:8333"),
                URI.create("http://localhost:8081"),
                "finanx-images",
                " ",
                "secret-key",
                Duration.ofMinutes(5)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("access-key");
    }

    @Test
    void shouldRejectMissingEndpointAndBucket() {
        assertThatThrownBy(() -> new SeaweedFsProperties(
                null,
                URI.create("http://localhost:8081"),
                "finanx-images",
                "access-key",
                "secret-key",
                Duration.ofMinutes(5)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("internal-endpoint");

        assertThatThrownBy(() -> new SeaweedFsProperties(
                URI.create("http://localhost:8333"),
                URI.create("http://localhost:8081"),
                " ",
                "access-key",
                "secret-key",
                Duration.ofMinutes(5)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket");
    }

    @Test
    void shouldRejectInvalidTtl() {
        assertThatThrownBy(() -> new SeaweedFsProperties(
                URI.create("http://localhost:8333"),
                URI.create("http://localhost:8081"),
                "finanx-images",
                "access-key",
                "secret-key",
                Duration.ZERO
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("presigned-url-ttl");
    }
}
