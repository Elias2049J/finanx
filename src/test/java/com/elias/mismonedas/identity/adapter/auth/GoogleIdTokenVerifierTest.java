package com.elias.mismonedas.identity.adapter.auth;

import com.elias.mismonedas.identity.dto.auth.GoogleIdTokenPayload;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenVerifierTest {

    private final GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier();

    @Test
    void shouldRejectTokenWithoutEmailVerifiedClaim() {
        Jwt jwt = jwt(Map.of("email", "ana@example.com", "sub", "google-1"));

        assertThatThrownBy(() -> verifier.toPayload(jwt))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    void shouldRejectExplicitlyUnverifiedEmail() {
        Jwt jwt = jwt(Map.of("email", "ana@example.com", "email_verified", false, "sub", "google-1"));

        assertThatThrownBy(() -> verifier.toPayload(jwt))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("not verified");
    }

    @Test
    void shouldRejectTokenWithoutEmail() {
        Jwt jwt = jwt(Map.of("email_verified", true, "sub", "google-1"));

        assertThatThrownBy(() -> verifier.toPayload(jwt))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("missing email");
    }

    @Test
    void shouldRejectTokenWithoutSubject() {
        Jwt jwt = jwt(Map.of("email", "ana@example.com", "email_verified", true));

        assertThatThrownBy(() -> verifier.toPayload(jwt))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("missing subject");
    }

    @Test
    void shouldNormalizeEmailOfVerifiedToken() {
        Jwt jwt = jwt(Map.of(
                "sub", "google-1",
                "email", "  Ana.Perez@Example.COM ",
                "email_verified", true,
                "given_name", "Ana",
                "family_name", "Perez"));

        GoogleIdTokenPayload payload = verifier.toPayload(jwt);

        assertThat(payload.subject()).isEqualTo("google-1");
        assertThat(payload.email()).isEqualTo("ana.perez@example.com");
        assertThat(payload.givenName()).isEqualTo("Ana");
        assertThat(payload.familyName()).isEqualTo("Perez");
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none");
        new HashMap<>(claims).forEach(builder::claim);
        return builder.build();
    }
}
