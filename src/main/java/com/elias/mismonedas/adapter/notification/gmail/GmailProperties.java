package com.elias.mismonedas.adapter.notification.gmail;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.notifications.gmail")
@Data
public class GmailProperties {
    private String host;
    private Integer port;
    private String from;
    private String username;
    private String appPassword;
}
