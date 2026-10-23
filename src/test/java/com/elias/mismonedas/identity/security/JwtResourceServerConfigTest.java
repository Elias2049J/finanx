package com.elias.mismonedas.identity.security;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.service.impl.JwtTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtResourceServerConfigTest {

    private JwtTokenServiceImpl jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenServiceImpl();
        ReflectionTestUtils.setField(jwtTokenService, "issuer", "test-issuer");
        ReflectionTestUtils.setField(jwtTokenService, "expirationSeconds", 3600L);
        ReflectionTestUtils.setField(jwtTokenService, "secret", "test-secret-that-is-at-least-32-characters");
        ReflectionTestUtils.invokeMethod(jwtTokenService, "init");
    }

    @Test
    void shouldExposeLocaleClaimOnSpringSecurityJwt() {
        User user = new User();
        user.setId(42L);
        user.setPreferences(UserPreferences.builder().locale("en-US").build());
        String token = jwtTokenService.generateToken(user);

        var decoded = new JwtResourceServerConfig(jwtTokenService).jwtDecoder().decode(token);

        assertThat(decoded.getClaimAsString(Constants.LOCALE_CLAIM)).isEqualTo("en-US");
    }
}
