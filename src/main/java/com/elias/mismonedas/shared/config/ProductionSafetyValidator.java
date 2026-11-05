package com.elias.mismonedas.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Falla el arranque cuando el backend se declara en produccion pero conserva
 * atajos de desarrollo o valores de ejemplo.
 *
 * <p>Se activa con {@code APP_ENV=production} o con un perfil activo
 * {@code prod}/{@code production}. En desarrollo no interviene.
 */
@Component
@Slf4j
public class ProductionSafetyValidator implements ApplicationRunner {

    private final Environment environment;
    private final String appEnvironment;
    private final boolean autoPremium;
    private final String googlePlayMode;
    private final String frontUrl;
    private final boolean springdocEnabled;
    private final String storageEndpoint;
    private final String jwtSecret;

    public ProductionSafetyValidator(
            Environment environment,
            @Value("${app.environment:development}") String appEnvironment,
            @Value("${app.billing.auto-premium:false}") boolean autoPremium,
            @Value("${app.billing.google-play.mode:disabled}") String googlePlayMode,
            @Value("${app.front-url:}") String frontUrl,
            @Value("${springdoc.api-docs.enabled:false}") boolean springdocEnabled,
            @Value("${app.storage.seaweedfs.public-endpoint:}") String storageEndpoint,
            @Value("${spring.security.jwt.secret:}") String jwtSecret) {
        this.environment = environment;
        this.appEnvironment = appEnvironment;
        this.autoPremium = autoPremium;
        this.googlePlayMode = googlePlayMode;
        this.frontUrl = frontUrl;
        this.springdocEnabled = springdocEnabled;
        this.storageEndpoint = storageEndpoint;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProduction()) {
            return;
        }

        List<String> violations = new ArrayList<>();
        if (autoPremium) {
            violations.add("APP_BILLING_AUTO_PREMIUM debe ser false en produccion.");
        }
        if ("fake".equalsIgnoreCase(googlePlayMode)) {
            violations.add("APP_BILLING_GOOGLE_PLAY_MODE no puede ser fake en produccion.");
        }
        if (springdocEnabled) {
            violations.add("SPRINGDOC_ENABLED debe ser false en produccion.");
        }
        requirePublicHttpsUrl("APP_FRONT_URL", frontUrl, violations);
        requirePublicHttpsUrl("SEAWEEDFS_PUBLIC_ENDPOINT", storageEndpoint, violations);
        if (jwtSecret == null || jwtSecret.isBlank()
                || jwtSecret.length() < 32
                || jwtSecret.toUpperCase(Locale.ROOT).contains("REEMPLAZAR")) {
            violations.add("JWT_SECRET debe ser un secreto real de al menos 32 caracteres.");
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Configuracion de produccion insegura:\n - "
                            + String.join("\n - ", violations));
        }
        log.info("Validacion de configuracion de produccion completada");
    }

    private boolean isProduction() {
        if (appEnvironment != null
                && "production".equalsIgnoreCase(appEnvironment.trim())) {
            return true;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod")
                        || profile.equalsIgnoreCase("production"));
    }

    private static void requirePublicHttpsUrl(
            String name, String value, List<String> violations) {
        if (value == null || value.isBlank()) {
            violations.add(name + " es obligatoria en produccion.");
            return;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://")) {
            violations.add(name + " debe usar https:// en produccion.");
        }
        if (lower.contains("localhost")
                || lower.contains("127.0.0.1")
                || lower.contains("ngrok")
                || lower.contains("10.0.2.2")) {
            violations.add(name + " no puede apuntar a un host de desarrollo.");
        }
    }
}
