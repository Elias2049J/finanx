package com.elias.mismonedas.document.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NginxStorageProxyConfigurationTest {

    @Test
    void shouldForwardTheOriginalHostIncludingPortToSeaweedFs() throws Exception {
        String nginxConfig = Files.readString(Path.of("nginx.conf"));
        int storageServerStart = nginxConfig.lastIndexOf("    server {");
        assertThat(storageServerStart).isGreaterThanOrEqualTo(0);

        String storageServerConfig = nginxConfig.substring(storageServerStart);
        assertThat(storageServerConfig)
                .contains("listen 8081;")
                .contains("proxy_pass http://storage;")
                .contains("proxy_set_header Host $http_host;");
    }
}
