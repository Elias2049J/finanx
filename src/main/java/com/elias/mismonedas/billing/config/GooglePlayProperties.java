package com.elias.mismonedas.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Configuracion de la integracion con Google Play Billing.
 * <p>
 * {@code mode=fake} usa tokens de prueba y no llama a Google. El modo
 * {@code disabled} (por defecto) no permite verificar compras. El modo
 * {@code google} verifica contra Android Publisher con un service account y
 * productos configurados en Play Console.
 */
@ConfigurationProperties(prefix = "app.billing.google-play")
public record GooglePlayProperties(
        String mode,
        String packageName,
        String monthlyProductId,
        String annualProductId,
        BigDecimal monthlyPrice,
        BigDecimal annualPrice,
        String currency,
        String serviceAccountCredentialsPath,
        String apiBaseUrl
) {
}
