package com.elias.mismonedas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        String mercadoPagoWebhookSecret,
        String culqiWebhookSecret,
        String stripeWebhookSecret
) {
}
