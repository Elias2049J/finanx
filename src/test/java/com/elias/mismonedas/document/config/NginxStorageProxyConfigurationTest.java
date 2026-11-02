package com.elias.mismonedas.document.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NginxStorageProxyConfigurationTest {

    @Test
    void shouldForwardTheOriginalHostIncludingPortToSeaweedFs() throws Exception {
        String nginxConfig = Files.readString(Path.of("nginx.conf"));
        String storageServerConfig = extractServerBlock(nginxConfig, "storage.mis-monedas.com");

        assertThat(storageServerConfig)
                .contains("listen 443 ssl")
                .contains("proxy_pass http://storage;")
                .contains("proxy_set_header Host $http_host;");
    }

    private String extractServerBlock(String nginxConfig, String serverName) {
        int serverNameIndex = nginxConfig.indexOf("server_name " + serverName + ";");
        assertThat(serverNameIndex).isGreaterThanOrEqualTo(0);

        int blockStart = nginxConfig.lastIndexOf("    server {", serverNameIndex);
        int blockEnd = nginxConfig.indexOf("\n    }", serverNameIndex);

        assertThat(blockStart).isGreaterThanOrEqualTo(0);
        assertThat(blockEnd).isGreaterThan(blockStart);

        return nginxConfig.substring(blockStart, blockEnd + 6);
    }
}
