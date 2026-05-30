package com.elias.finanx.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.elias.finanx.entity.User;
import com.elias.finanx.security.Constants;
import com.elias.finanx.service.JwtTokenService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class JwtTokenServiceImpl implements JwtTokenService {

    @Value("${spring.security.jwt.issuer:credimacpato-api}")
    private String issuer;

    @Value("${spring.security.jwt.expiration-seconds:3600}")
    private long expirationSeconds;

    @Value("${spring.security.jwt.secret}")
    private String secret;

    private Algorithm algorithm;

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret is missing (security.jwt.secret)");
        }
        if (secret.length() < 32) {
            log.warn("JWT secret is too short; use at least 32 characters");
        }
        this.algorithm = Algorithm.HMAC256(secret);
    }

    @Override
    public String generateToken(UserDetails user) {
        Instant now = Instant.now();

        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String subject = "";
        if (user instanceof User u) {
            subject = u.getEmail().trim().toLowerCase();
        }

        return JWT.create()
                .withIssuer(issuer)
                .withSubject(subject)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(expirationSeconds)))
                .withClaim(Constants.AUTHORITIES_CLAIM, authorities)
                .sign(algorithm);
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