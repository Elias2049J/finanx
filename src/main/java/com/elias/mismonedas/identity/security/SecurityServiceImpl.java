package com.elias.mismonedas.identity.security;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.Role;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.domain.enums.UserState;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;


@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class SecurityServiceImpl implements SecurityService {

    private User currentUser;
    private boolean resolved;

    @Override
    public User getCurrentUser() {
        if (resolved) {
            return currentUser;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }

        Jwt jwt = jwtOf(authentication);
        if (jwt == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }

        currentUser = fromClaims(jwt);
        resolved = true;
        return currentUser;
    }

    private static Jwt jwtOf(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        return authentication.getPrincipal() instanceof Jwt jwt ? jwt : null;
    }

    private static User fromClaims(Jwt jwt) {
        User user = new User();
        user.setId(parseId(jwt.getSubject()));
        user.setEmail(jwt.getClaimAsString(Constants.EMAIL_CLAIM));
        user.setName(jwt.getClaimAsString(Constants.NAME_CLAIM));
        user.setLastname(jwt.getClaimAsString(Constants.LASTNAME_CLAIM));
        user.setRole(parseEnum(jwt.getClaimAsString(Constants.ROLE_CLAIM), Role.class, Role.USER));
        user.setState(parseEnum(jwt.getClaimAsString(Constants.STATE_CLAIM), UserState.class, UserState.ENABLED));
        user.setPreferences(UserPreferences.builder()
                .timeZone(parseEnum(jwt.getClaimAsString(Constants.TIMEZONE_CLAIM),
                        TimeZone.class, TimeZone.AMERICA_LIMA))
                .currencyCode(jwt.getClaimAsString(Constants.CURRENCY_CLAIM))
                .build());
        return user;
    }




    private static Long parseId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException ex) {
            throw new AuthenticationCredentialsNotFoundException("Malformed subject in token");
        }
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
