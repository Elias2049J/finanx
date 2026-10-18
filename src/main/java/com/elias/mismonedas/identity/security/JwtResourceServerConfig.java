package com.elias.mismonedas.identity.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.elias.mismonedas.identity.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class JwtResourceServerConfig {

    private final JwtTokenService jwtTokenService;

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            try {
                var decoded = jwtTokenService.verify(token);

                Instant issuedAt = decoded.getIssuedAt() == null ? null : decoded.getIssuedAt().toInstant();
                Instant expiresAt = decoded.getExpiresAt() == null ? null : decoded.getExpiresAt().toInstant();

                Map<String, Object> headers = new HashMap<>();
                headers.put("alg", decoded.getAlgorithm());
                headers.put("typ", "JWT");

                Map<String, Object> claims = new HashMap<>();
                claims.put("sub", decoded.getSubject());
                claims.put("iss", decoded.getIssuer());
                claims.put(Constants.AUTHORITIES_CLAIM, jwtTokenService.extractAuthorities(decoded));
                copyClaim(claims, decoded, Constants.EMAIL_CLAIM);
                copyClaim(claims, decoded, Constants.ROLE_CLAIM);
                copyClaim(claims, decoded, Constants.STATE_CLAIM);
                copyClaim(claims, decoded, Constants.TIMEZONE_CLAIM);
                copyClaim(claims, decoded, Constants.CURRENCY_CLAIM);

                return new Jwt(token, issuedAt, expiresAt, headers, claims);
            } catch (Exception ex) {
                throw new JwtException("Invalid JWT", ex);
            }
        };
    }

    private static void copyClaim(Map<String, Object> claims, DecodedJWT decoded, String name) {
        String value = decoded.getClaim(name).asString();
        if (value != null) {
            claims.put(name, value);
        }
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName(Constants.AUTHORITIES_CLAIM);
        grantedAuthoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
