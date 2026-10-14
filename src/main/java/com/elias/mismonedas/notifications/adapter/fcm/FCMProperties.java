package com.elias.mismonedas.notifications.adapter.fcm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifications.fcm")
public record FCMProperties(
        String credentialsPath,
        String projectId
) { }
