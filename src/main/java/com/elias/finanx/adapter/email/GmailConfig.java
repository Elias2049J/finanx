package com.elias.finanx.adapter.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GmailProperties.class)
public class GmailConfig {
}
