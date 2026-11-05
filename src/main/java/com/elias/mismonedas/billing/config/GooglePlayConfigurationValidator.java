package com.elias.mismonedas.billing.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Valida la configuracion de Google Play al arrancar. En modo google un error
 * de configuracion debe impedir el arranque, no dejar el endpoint abierto sin
 * verificador o con un verificador mal configurado.
 */
@Component
@RequiredArgsConstructor
public class GooglePlayConfigurationValidator {

    private static final Set<String> ALLOWED_MODES = Set.of("disabled", "fake", "google");

    private final GooglePlayProperties properties;

    @PostConstruct
    void validate() {
        String mode = properties.mode() == null
                ? "disabled"
                : properties.mode().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MODES.contains(mode)) {
            throw new IllegalStateException(
                    "APP_BILLING_GOOGLE_PLAY_MODE debe ser disabled, fake o google; valor: " + properties.mode());
        }
        if (!"google".equals(mode)) {
            return;
        }
        if (properties.packageName() == null || properties.packageName().isBlank()) {
            throw new IllegalStateException(
                    "APP_BILLING_GOOGLE_PLAY_PACKAGE_NAME es obligatoria con mode=google");
        }
        if (properties.serviceAccountCredentialsPath() == null
                || properties.serviceAccountCredentialsPath().isBlank()) {
            throw new IllegalStateException(
                    "APP_BILLING_GOOGLE_PLAY_SERVICE_ACCOUNT_PATH es obligatoria con mode=google");
        }
        if (properties.apiBaseUrl() == null || properties.apiBaseUrl().isBlank()
                || !properties.apiBaseUrl().toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new IllegalStateException(
                    "APP_BILLING_GOOGLE_PLAY_API_BASE_URL debe usar https:// con mode=google");
        }
        if (properties.monthlyProductId() == null || properties.monthlyProductId().isBlank()
                || properties.annualProductId() == null || properties.annualProductId().isBlank()) {
            throw new IllegalStateException(
                    "Los product ids mensual y anual son obligatorios con mode=google");
        }
    }
}
