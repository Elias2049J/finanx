package com.elias.mismonedas.identity.service.impl;

import com.auth0.jwt.JWT;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceImplTest {

    private JwtTokenServiceImpl jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenServiceImpl();
        ReflectionTestUtils.setField(jwtTokenService, "issuer", "test-issuer");
        ReflectionTestUtils.setField(jwtTokenService, "expirationSeconds", 3600L);
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
}
