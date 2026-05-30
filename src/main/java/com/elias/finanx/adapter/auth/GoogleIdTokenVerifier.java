package com.elias.finanx.adapter.auth;

import com.elias.finanx.dto.auth.GoogleIdTokenPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GoogleIdTokenVerifier {
    @Value("${app.google.issuer}")
    private String googleIssuer;
    @Value("${app.google.jwks}")
    private String GOOGLE_JWKS;

    private final JwtDecoder googleJwtDecoder;

    public GoogleIdTokenVerifier() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(googleIssuer);
        decoder.setJwtValidator(issuerValidator);
        this.googleJwtDecoder = decoder;
    }

    public GoogleIdTokenPayload verify(String idToken, String expectedAudience) {
        if (idToken == null || idToken.isBlank()) {
            throw new BadCredentialsException("Missing Google id_token");
        }
        if (expectedAudience == null || expectedAudience.isBlank()) {
            throw new IllegalStateException("Missing Google client-id for id_token verification");
        }

        NimbusJwtDecoder decoder = (NimbusJwtDecoder) this.googleJwtDecoder;

        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(googleIssuer),
                audienceValidator(expectedAudience)
        );
        decoder.setJwtValidator(withAudience);

        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
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

    private static OAuth2TokenValidator<Jwt> audienceValidator(String expectedAud) {
        return token -> {
            List<String> aud = token.getAudience();
            if (aud != null && aud.contains(expectedAud)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error err = new OAuth2Error("invalid_token", "Invalid audience", null);
            return OAuth2TokenValidatorResult.failure(err);
        };
    }
}

