package com.elias.finanx.adapter.notification.fcm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifications.fcm")
public record FCMProperties(
        String credentialsPath,
        String projectId
) { }
