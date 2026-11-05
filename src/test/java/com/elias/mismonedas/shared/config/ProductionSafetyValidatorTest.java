package com.elias.mismonedas.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ProductionSafetyValidatorTest {

    private static final String SAFE_JWT_SECRET =
            "a-real-production-secret-with-more-than-32-characters";

    private final ApplicationArguments arguments = mock(ApplicationArguments.class);

    @Test
    void doesNothingOutsideProduction() {
        MockEnvironment environment = new MockEnvironment();
        ProductionSafetyValidator validator = validator(
                environment, "development", true, "fake",
                "http://localhost:4200", true,
                "http://localhost:8081", "short", false);

        assertThatCode(() -> validator.run(arguments)).doesNotThrowAnyException();
    }

    @Test
    void rejectsDevelopmentShortcutsInProduction() {
        MockEnvironment environment = new MockEnvironment();
        ProductionSafetyValidator validator = validator(
                environment, "production", true, "fake",
                "http://localhost:4200", true,
                "http://localhost:8081", "short", true);

        assertThatThrownBy(() -> validator.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BILLING_AUTO_PREMIUM")
                .hasMessageContaining("APP_BILLING_GOOGLE_PLAY_MODE")
                .hasMessageContaining("SPRINGDOC_ENABLED")
                .hasMessageContaining("APP_FRONT_URL")
                .hasMessageContaining("SEAWEEDFS_PUBLIC_ENDPOINT")
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("APP_DEV_SEED_USER_ENABLED");
    }

    @Test
    void acceptsSafeProductionConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        ProductionSafetyValidator validator = validator(
                environment, "production", false, "disabled",
                "https://app.mis-monedas.com", false,
                "https://storage.mis-monedas.com", SAFE_JWT_SECRET, false);

        assertThatCode(() -> validator.run(arguments)).doesNotThrowAnyException();
    }

    @Test
    void rejectsProductionProfileEvenWhenAppEnvironmentIsDevelopment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ProductionSafetyValidator validator = validator(
                environment, "development", true, "disabled",
                "https://app.mis-monedas.com", false,
                "https://storage.mis-monedas.com", SAFE_JWT_SECRET, false);

        assertThatThrownBy(() -> validator.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BILLING_AUTO_PREMIUM");
    }

    private static ProductionSafetyValidator validator(
            MockEnvironment environment,
            String appEnvironment,
            boolean autoPremium,
            String googlePlayMode,
            String frontUrl,
            boolean springdocEnabled,
            String storageEndpoint,
            String jwtSecret,
            boolean devSeedUserEnabled) {
        return new ProductionSafetyValidator(
                environment,
                appEnvironment,
                autoPremium,
                googlePlayMode,
                frontUrl,
                springdocEnabled,
                storageEndpoint,
                jwtSecret,
                devSeedUserEnabled);
    }
}
