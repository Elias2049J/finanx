package com.elias.mismonedas.identity.controller;

import com.elias.mismonedas.identity.adapter.auth.GoogleAuthService;
import com.elias.mismonedas.identity.dto.auth.LogoutRequest;
import com.elias.mismonedas.identity.domain.event.UserLoggedOutEvent;
import com.elias.mismonedas.identity.service.AuthService;
import com.elias.mismonedas.identity.service.UserService;
import com.elias.mismonedas.identity.service.AuthTokenService;
import com.elias.mismonedas.identity.service.EmailVerificationService;
import com.elias.mismonedas.identity.service.EmailVerificationChallengeManager;
import com.elias.mismonedas.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private GoogleAuthService googleAuthService;
    @Mock private UserService userService;
    @Mock private AuthTokenService authTokenService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private EmailVerificationChallengeManager emailVerificationChallengeManager;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                authService, googleAuthService, userService, authTokenService,
                userRepository, emailVerificationService, emailVerificationChallengeManager, eventPublisher);
    }

    @Test
    void shouldRevokeRefreshFamilyAndRemoveOnlyTheSubmittedDeviceToken() {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("refresh-token");
        request.setFcmToken("device-fcm-token");
        when(authTokenService.revokeAndGetUserId("refresh-token")).thenReturn(Optional.of(17L));

        var response = controller.logout(request);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(authTokenService).revokeAndGetUserId("refresh-token");
        verify(eventPublisher).publishEvent(new UserLoggedOutEvent(17L, "device-fcm-token"));
    }

    @Test
    void shouldRemainCompatibleWhenOlderClientOmitsDeviceToken() {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("refresh-token");
        when(authTokenService.revokeAndGetUserId("refresh-token")).thenReturn(Optional.of(17L));

        var response = controller.logout(request);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(authTokenService).revokeAndGetUserId("refresh-token");
        verify(eventPublisher, never()).publishEvent(new UserLoggedOutEvent(17L, null));
    }

    @Test
    void shouldNotRemoveDeviceTokenWhenRefreshTokenCannotIdentifyAnOwner() {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("unknown-refresh-token");
        request.setFcmToken("device-fcm-token");
        when(authTokenService.revokeAndGetUserId("unknown-refresh-token")).thenReturn(Optional.empty());

        var response = controller.logout(request);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(eventPublisher, never()).publishEvent(new UserLoggedOutEvent(17L, "device-fcm-token"));
    }
}
