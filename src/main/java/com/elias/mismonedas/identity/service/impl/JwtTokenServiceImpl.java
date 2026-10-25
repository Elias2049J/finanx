package com.elias.mismonedas.identity.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.SupportedLocale;
import com.elias.mismonedas.identity.security.Constants;
import com.elias.mismonedas.identity.service.JwtTokenService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class JwtTokenServiceImpl implements JwtTokenService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final long MIN_EXPIRATION_SECONDS = 300;
    private static final long MAX_EXPIRATION_SECONDS = 900;
    private static final Set<String> PLACEHOLDER_SECRETS = Set.of(
            "reemplazar",
            "replace-me",
            "change-me",
            "changeme",
            "your-secret",
            "your-secret-here"
    );

    @Value("${spring.security.jwt.issuer:credimacpato-api}")
    private String issuer;

    @Value("${spring.security.jwt.expiration-seconds:900}")
    private long expirationSeconds;

    @Value("${spring.security.jwt.secret}")
    private String secret;

    private Algorithm algorithm;

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT signing secret is missing");
        }
        String normalizedSecret = secret.trim().toLowerCase(Locale.ROOT);
        boolean placeholder = PLACEHOLDER_SECRETS.contains(normalizedSecret)
                || normalizedSecret.startsWith("reemplazar_");
        if (placeholder || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT signing secret must be a non-placeholder with at least 32 UTF-8 bytes");
        }
        if (expirationSeconds < MIN_EXPIRATION_SECONDS || expirationSeconds > MAX_EXPIRATION_SECONDS) {
            throw new IllegalStateException("JWT access-token expiration must be between 300 and 900 seconds");
        }
        this.algorithm = Algorithm.HMAC256(secret);
    }

    @Override
    public String generateToken(UserDetails details) {
        Instant now = Instant.now();

        List<String> authorities = details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String subject = "";
        String email = null;
        String name = null;
        String lastname = null;
        String role = null;
        String state = null;
        String locale = SupportedLocale.DEFAULT_TAG;

        if (details instanceof User u) {
            subject = String.valueOf(u.getId());
            email = u.getEmail() == null ? null : u.getEmail().trim().toLowerCase();
            name = u.getName();
            lastname = u.getLastname();
            role = u.getRole() == null ? null : u.getRole().name();
            state = u.getState() == null ? null : u.getState().name();
            if (u.getPreferences() != null) {
                locale = SupportedLocale.canonicalizeOrDefault(u.getPreferences().getLocale());
            }
        }

        var creator = JWT.create()
                .withIssuer(issuer)
                .withSubject(subject)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(expirationSeconds)))
                .withClaim(Constants.AUTHORITIES_CLAIM, authorities)
                .withClaim(Constants.LOCALE_CLAIM, locale);

        if (email != null) {
            creator.withClaim(Constants.EMAIL_CLAIM, email);
        }
        if (name != null) {
            creator.withClaim(Constants.NAME_CLAIM, name);
        }
        if (lastname != null) {
            creator.withClaim(Constants.LASTNAME_CLAIM, lastname);
        }
        if (role != null) {
            creator.withClaim(Constants.ROLE_CLAIM, role);
        }
        if (state != null) {
            creator.withClaim(Constants.STATE_CLAIM, state);
        }

        return creator.sign(algorithm);
    }

    @Override
    public DecodedJWT verify(String token) {
        return verifier().verify(token);
    }

    @Override
    public Collection<String> extractAuthorities(DecodedJWT jwt) {
        if (jwt == null) {
            return List.of();
        }
        List<String> values = jwt.getClaim(Constants.AUTHORITIES_CLAIM).asList(String.class);
        return values == null ? List.of() : values;
    }

    private JWTVerifier verifier() {
        return JWT.require(algorithm)
                .withIssuer(issuer)
                .build();
    }
}
