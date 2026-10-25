package com.elias.mismonedas.identity.security;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.UserState;
import com.elias.mismonedas.identity.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SecurityServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SecurityServiceImpl securityService = new SecurityServiceImpl(userRepository);

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void shouldResolveOnlyUserWithTheMatchingActiveRefreshFamily() {
        User user = user(81L, UserState.ENABLED);
        when(userRepository.findByIdAndActiveRefreshFamily(
                eq(81L), eq("device-family"), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(user));
        authenticate("81", "device-family");

        assertThat(securityService.getCurrentUser()).isSameAs(user);
    }

    @Test
    void shouldRejectTokenWithoutSessionIdBeforeReadingUser() {
        authenticate("81", null);

        assertThatThrownBy(securityService::getCurrentUser)
                .isInstanceOf(InvalidBearerTokenException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectRevokedOrExpiredRefreshFamily() {
        when(userRepository.findByIdAndActiveRefreshFamily(
                eq(81L), eq("revoked-family"), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());
        authenticate("81", "revoked-family");

        assertThatThrownBy(securityService::getCurrentUser)
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void shouldRejectDisabledUserEvenIfFamilyLookupReturnsAUser() {
        when(userRepository.findByIdAndActiveRefreshFamily(
                eq(81L), eq("disabled-family"), any(OffsetDateTime.class)))
                .thenReturn(Optional.of(user(81L, UserState.DISABLED)));
        authenticate("81", "disabled-family");

        assertThatThrownBy(securityService::getCurrentUser)
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void shouldSurfaceDatabaseOutageAsAuthenticationServiceFailure() {
        when(userRepository.findByIdAndActiveRefreshFamily(
                eq(81L), eq("device-family"), any(OffsetDateTime.class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        authenticate("81", "device-family");

        assertThatThrownBy(securityService::getCurrentUser)
                .isInstanceOf(AuthenticationServiceException.class);
    }

    private void authenticate(String subject, String sessionId) {
        Jwt.Builder builder = Jwt.withTokenValue("signed-token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900));
        if (sessionId != null) {
            builder.claim(Constants.SESSION_ID_CLAIM, sessionId);
        }
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(builder.build(), List.of()));
    }

    private User user(Long id, UserState state) {
        User user = new User();
        user.setId(id);
        user.setState(state);
        return user;
    }
}
