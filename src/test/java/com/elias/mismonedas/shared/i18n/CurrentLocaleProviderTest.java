package com.elias.mismonedas.shared.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentLocaleProviderTest {

    private final CurrentLocaleProvider provider = new CurrentLocaleProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReadSupportedLocaleFromJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("locale", "en-US")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(provider.currentLocale()).isEqualTo(Locale.forLanguageTag("en-US"));
    }

    @Test
    void shouldFallbackToSpanishWithoutSupportedJwtLocale() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("locale", "fr-FR")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(provider.currentLocale()).isEqualTo(Locale.forLanguageTag("es-PE"));
    }
}
