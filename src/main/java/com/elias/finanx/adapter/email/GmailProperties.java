package com.elias.finanx.adapter.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gmail")
@Data
public class GmailProperties {
    private String host;
    private Integer port;
    private String from;
    private String username;
    private String appPassword;
}
