package com.elias.mismonedas.identity.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.service.impl.JwtTokenServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtResourceServerConfigTest {

    private static final String TEST_SECRET = "test-secret-that-is-at-least-32-characters";
    private static final String TEST_ISSUER = "test-issuer";

    private JwtTokenServiceImpl jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenServiceImpl();
        ReflectionTestUtils.setField(jwtTokenService, "issuer", TEST_ISSUER);
        ReflectionTestUtils.setField(jwtTokenService, "expirationSeconds", 900L);
        ReflectionTestUtils.setField(jwtTokenService, "secret", TEST_SECRET);
        ReflectionTestUtils.invokeMethod(jwtTokenService, "init");
    }

    @Test
    void shouldExposeLocaleClaimOnSpringSecurityJwt() {
        User user = new User();
        user.setId(42L);
        user.setPreferences(UserPreferences.builder().locale("en-US").build());
        String token = jwtTokenService.generateToken(user, "device-family");

        var decoded = new JwtResourceServerConfig(jwtTokenService).jwtDecoder().decode(token);

        assertThat(decoded.getClaimAsString(Constants.LOCALE_CLAIM)).isEqualTo("en-US");
        assertThat(decoded.getClaimAsString(Constants.SESSION_ID_CLAIM)).isEqualTo("device-family");
    }

    @Test
    void shouldRejectLegacyJwtWithoutSessionId() {
        String token = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject("42")
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(300)))
                .sign(Algorithm.HMAC256(TEST_SECRET));

        assertThatThrownBy(() -> new JwtResourceServerConfig(jwtTokenService).jwtDecoder().decode(token))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }

    @Test
    void shouldRejectJwtWithNonStringSessionId() {
        String token = JWT.create()
                .withIssuer(TEST_ISSUER)
                .withSubject("42")
                .withIssuedAt(Date.from(Instant.now()))
                .withExpiresAt(Date.from(Instant.now().plusSeconds(300)))
                .withClaim(Constants.SESSION_ID_CLAIM, 42L)
                .sign(Algorithm.HMAC256(TEST_SECRET));

        assertThatThrownBy(() -> new JwtResourceServerConfig(jwtTokenService).jwtDecoder().decode(token))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
}
