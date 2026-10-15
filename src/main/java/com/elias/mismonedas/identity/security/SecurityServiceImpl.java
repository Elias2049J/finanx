package com.elias.mismonedas.identity.security;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** Resolves and caches the JWT-authenticated user once per HTTP request. */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class SecurityServiceImpl implements SecurityService {

    private final UserRepository userRepository;

    private User currentUser;
    private boolean resolved;

    @Override
    @Transactional(readOnly = true)
    public User getCurrentUser() {
        if (resolved) {
            return currentUser;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }

        String email = authentication.getName();
        currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user not found: " + email));
        resolved = true;
        return currentUser;
    }
}
