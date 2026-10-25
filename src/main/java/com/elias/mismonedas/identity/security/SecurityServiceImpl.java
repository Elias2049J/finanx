package com.elias.mismonedas.identity.security;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;

@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class SecurityServiceImpl implements SecurityService {

    private final UserRepository userRepository;

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

        Long userId = parseId(jwt.getSubject());
        Object rawSessionId = jwt.getClaims().get(Constants.SESSION_ID_CLAIM);
        if (!(rawSessionId instanceof String sessionId) || sessionId.isBlank()) {
            throw new InvalidBearerTokenException("Invalid session claim");
        }

        User user;
        try {
            user = userRepository.findByIdAndActiveRefreshFamily(userId, sessionId, OffsetDateTime.now())
                    .orElseThrow(() -> new InvalidBearerTokenException("Session is no longer active"));
        } catch (DataAccessException ex) {
            throw new AuthenticationServiceException("Could not validate session state", ex);
        }

        if (user.getState() == null || !user.isEnabled()) {
            throw new InvalidBearerTokenException("User account is not enabled");
        }

        currentUser = user;
        resolved = true;
        return currentUser;
    }

    private static Jwt jwtOf(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        return authentication.getPrincipal() instanceof Jwt jwt ? jwt : null;
    }

    private static Long parseId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException ex) {
            throw new InvalidBearerTokenException("Malformed subject in token", ex);
        }
    }
}
