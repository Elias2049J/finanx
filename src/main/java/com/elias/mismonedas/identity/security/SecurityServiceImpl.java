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

/**
 * Resolves and caches the JWT-authenticated user once per HTTP request.
 *
 * <p>F11 (token self-sufficient): el usuario se reconstruye desde los claims del token ya
 * verificado, por lo que <b>no consulta {@code UserRepository}</b>. Antes cada request
 * autenticado hacia un {@code findByEmail} contra {@code usuarios}.
 *
 * <p><b>Importante:</b> la instancia devuelta esta <b>desconectada</b>. Sirve para leer
 * {@code id/email/role/state} y para fijar la FK en entidades hijas ({@code setUser(...)}),
 * pero nunca para persistir el propio {@code User}. Lleva ademas una
 * {@code UserPreferences} tambien desconectada y <b>solo</b> con {@code timeZone} y
 * {@code currencyCode}, que son los dos datos que {@code User.getTimeZone()} y
 * {@code getPreferences().getCurrencyCode()} leen a traves de esa asociacion.
 */
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
        user.setRole(parseEnum(jwt.getClaimAsString(Constants.ROLE_CLAIM), Role.class, Role.USER));
        user.setState(parseEnum(jwt.getClaimAsString(Constants.STATE_CLAIM), UserState.class, UserState.ENABLED));
        user.setPreferences(UserPreferences.builder()
                .timeZone(parseEnum(jwt.getClaimAsString(Constants.TIMEZONE_CLAIM),
                        TimeZone.class, TimeZone.AMERICA_LIMA))
                .currencyCode(jwt.getClaimAsString(Constants.CURRENCY_CLAIM))
                .build());
        return user;
    }

    /**
     * Un token con subject que no sea numerico es un token viejo (F11 ponia el email en
     * {@code sub}) o uno emitido por otro emisor: se rechaza en vez de fallar despues.
     */
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
