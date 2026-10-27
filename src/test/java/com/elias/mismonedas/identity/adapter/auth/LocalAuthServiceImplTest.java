package com.elias.mismonedas.identity.adapter.auth;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.UserState;
import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.dto.auth.LoginRequest;
import com.elias.mismonedas.identity.service.AuthTokenService;
import com.elias.mismonedas.identity.service.EmailNotVerifiedException;
import com.elias.mismonedas.identity.service.UserService;
import com.elias.mismonedas.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalAuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserService userService;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private Authentication authentication;

    private LocalAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LocalAuthServiceImpl(authenticationManager, userService, authTokenService);
    }

    @Test
    void loginCancelsPendingDeletionBeforeIssuingTokens() {
        User user = pendingDeletion(true, OffsetDateTime.now().plusDays(1));
        AuthResponse response = new AuthResponse("access", "refresh", 900L);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(authTokenService.issue(user)).thenReturn(response);

        assertThat(service.login(loginRequest())).isSameAs(response);

        InOrder order = inOrder(userService, authTokenService);
        order.verify(userService).cancelAccountDeletion(user.getId());
        order.verify(authTokenService).issue(user);
    }

    @Test
    void unverifiedPendingAccountCannotCancelDeletionThroughLogin() {
        User user = pendingDeletion(false, OffsetDateTime.now().plusDays(1));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOf(EmailNotVerifiedException.class);

        verifyNoInteractions(userService, authTokenService);
    }

    @Test
    void expiredPendingDeletionDoesNotIssueTokensIfDeadlinePassesAfterAuthentication() {
        User user = pendingDeletion(true, OffsetDateTime.now().minusSeconds(1));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        doThrow(new ConflictException("deletion deadline passed"))
                .when(userService).cancelAccountDeletion(user.getId());

        assertThatThrownBy(() -> service.login(loginRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("deletion deadline passed");

        verifyNoInteractions(authTokenService);
    }

    private User pendingDeletion(boolean emailVerified, OffsetDateTime scheduledAt) {
        User user = new User();
        user.setId(7L);
        user.setEmailVerified(emailVerified);
        user.setState(UserState.ENABLED);
        user.requestDeletion(scheduledAt.minusDays(7), scheduledAt);
        return user;
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.test");
        request.setPassword("correct-password");
        return request;
    }
}
