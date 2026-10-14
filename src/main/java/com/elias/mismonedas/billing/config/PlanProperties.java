package com.elias.mismonedas.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.plans")
public record PlanProperties(
        int trialDays
) {
}
