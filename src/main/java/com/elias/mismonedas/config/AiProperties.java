package com.elias.mismonedas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        int maxUserPromptLength,
        int maxOutputTokens,
        int advisorTemperature,
        int chitchatTemperature,
        int timeoutMs
) { }
