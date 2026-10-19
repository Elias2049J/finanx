package com.elias.mismonedas.identity.adapter.auth;

import com.elias.mismonedas.identity.dto.auth.GoogleIdTokenPayload;
import com.elias.mismonedas.shared.config.HttpRequestFactories;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Component
public class GoogleIdTokenVerifier {
    @Value("${app.google.issuer}")
    private String googleIssuer;
    @Value("${app.google.jwks}")
    private String googleJwks;
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;
    @Value("${app.http.connect-timeout:5s}")
    private Duration connectTimeout;
    @Value("${app.http.read-timeout:10s}")
    private Duration readTimeout;

    private JwtDecoder googleJwtDecoder;

    @PostConstruct
    void init() {
        if (googleIssuer == null || googleIssuer.isBlank()) {
            throw new IllegalStateException("Google issuer is missing (app.google.issuer)");
        }

        if (googleJwks == null || googleJwks.isBlank()) {
            throw new IllegalStateException("Google jwks is missing (app.google.jwks)");
        }

        if (googleClientId == null || googleClientId.isBlank()) {
            throw new IllegalStateException(
                    "Google client-id is missing (spring.security.oauth2.client.registration.google.client-id)");
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(googleJwks)
                .restOperations(new RestTemplate(
                        HttpRequestFactories.create(connectTimeout, readTimeout)))
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(List.of(
                new JwtIssuerValidator(googleIssuer),
                new JwtAudienceValidator(googleClientId))));
        this.googleJwtDecoder = decoder;
    }

    public GoogleIdTokenPayload verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BadCredentialsException("Missing Google id_token");
        }

        Jwt jwt;
        try {
            jwt = googleJwtDecoder.decode(idToken);
        } catch (JwtException ex) {
            throw new BadCredentialsException("Invalid Google id_token", ex);
        }

        String email = jwt.getClaimAsString("email");
        Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
        if (email == null || email.isBlank()) {
            throw new BadCredentialsException("Google id_token missing email");
        }
        if (emailVerified != null && !emailVerified) {
            throw new BadCredentialsException("Google email not verified");
        }

        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");

        return new GoogleIdTokenPayload(email, givenName, familyName);
    }
}
