package com.elias.mismonedas.notifications.adapter.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.notifications.email")
@Data
public class EmailProperties {
    private String host;
    private Integer port;
    private String from;
    private String username;
    private String appPassword;
}
