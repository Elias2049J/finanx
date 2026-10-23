package com.elias.mismonedas.shared.i18n;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class JsonMessageCatalogTest {

    private JsonMessageCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new JsonMessageCatalog(new ObjectMapper());
        catalog.initialize();
    }

    @Test
    void shouldResolveLocalizedMessageAndArguments() {
        assertThat(catalog.getMessage("notification.budget.exceeded.text", Locale.forLanguageTag("en-US"),
                "Food", "120.00", "PEN", "100.00", "25.50", "Cash"))
                .contains("You exceeded", "Food", "120.00");
    }

    @Test
    void shouldUseSpanishWhenLocaleOrTranslationIsMissing() {
        assertThat(catalog.getMessage("catalog.fallback.test", Locale.forLanguageTag("en-US")))
                .isEqualTo("Texto de respaldo en español.");
        assertThat(catalog.getMessage("error.invalid_argument", Locale.forLanguageTag("fr-FR")))
                .isEqualTo("La solicitud contiene datos no válidos.");
    }
}
