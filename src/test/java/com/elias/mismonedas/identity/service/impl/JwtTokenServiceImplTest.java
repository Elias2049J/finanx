package com.elias.mismonedas.identity.service.impl;

import com.auth0.jwt.JWT;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceImplTest {

    private JwtTokenServiceImpl jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenServiceImpl();
        ReflectionTestUtils.setField(jwtTokenService, "issuer", "test-issuer");
        ReflectionTestUtils.setField(jwtTokenService, "expirationSeconds", 900L);
        ReflectionTestUtils.setField(jwtTokenService, "secret", "test-secret-that-is-at-least-32-characters");
        jwtTokenService.init();
    }

    @Test
    void shouldIncludeUserLocaleInAccessToken() {
        User user = new User();
        user.setId(42L);
        user.setPreferences(UserPreferences.builder().locale("en-US").build());

        String token = jwtTokenService.generateToken(user);

        assertThat(JWT.decode(token).getClaim("locale").asString()).isEqualTo("en-US");
    }

    @Test
    void shouldUseDefaultLocaleWhenUserHasNoPreferences() {
        User user = new User();
        user.setId(42L);

        String token = jwtTokenService.generateToken(user);

        assertThat(JWT.decode(token).getClaim("locale").asString()).isEqualTo("es-PE");
    }

    @Test
    void shouldRejectSecretShorterThan32Utf8Bytes() {
        JwtTokenServiceImpl service = newService("short secret");

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");
    }

    @Test
    void shouldRejectLongPlaceholderSecret() {
        JwtTokenServiceImpl service = newService("REEMPLAZAR_CON_UN_SECRETO_ALEATORIO_DE_32_BYTES");

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");
    }

    @Test
    void shouldRejectAccessTokenTtlAbove15Minutes() {
        JwtTokenServiceImpl service = newService("test-secret-that-is-at-least-32-characters");
        ReflectionTestUtils.setField(service, "expirationSeconds", 84_600L);

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expiration");
    }

    @Test
    void shouldRejectAccessTokenTtlBelowFiveMinutes() {
        JwtTokenServiceImpl service = newService("test-secret-that-is-at-least-32-characters");
        ReflectionTestUtils.setField(service, "expirationSeconds", 299L);

        assertThatThrownBy(service::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expiration");
    }

    @Test
    void shouldMeasureSecretStrengthInUtf8BytesRatherThanCharacters() {
        JwtTokenServiceImpl service = newService("€".repeat(11));

        assertThatNoException().isThrownBy(service::init);
    }

    private JwtTokenServiceImpl newService(String secret) {
        JwtTokenServiceImpl service = new JwtTokenServiceImpl();
        ReflectionTestUtils.setField(service, "issuer", "test-issuer");
        ReflectionTestUtils.setField(service, "expirationSeconds", 900L);
        ReflectionTestUtils.setField(service, "secret", secret);
        return service;
    }
}
