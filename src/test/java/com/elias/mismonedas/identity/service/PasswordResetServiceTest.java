package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.AuthProvider;
import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import com.elias.mismonedas.identity.domain.enums.Role;
import com.elias.mismonedas.identity.domain.enums.UserState;
import com.elias.mismonedas.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuthTokenService authTokenService = mock(AuthTokenService.class);

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                userRepository, emailVerificationService, passwordEncoder, authTokenService);
    }

    @Test
    void shouldIssueResetCodeForVerifiedLocalAccount() {
        User user = localUser();
        when(userRepository.findByEmail("alice@example.test")).thenReturn(Optional.of(user));

        service.requestReset(" Alice@Example.test ");

        verify(emailVerificationService).issueChallenge(
                "alice@example.test", EmailVerificationPurpose.PASSWORD_RESET);
    }

    @Test
    void shouldNotRevealMissingOrGoogleAccounts() {
        when(userRepository.findByEmail("missing@example.test")).thenReturn(Optional.empty());
        User googleUser = localUser();
        googleUser.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findByEmail("google@example.test")).thenReturn(Optional.of(googleUser));

        service.requestReset("missing@example.test");
        service.requestReset("google@example.test");

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldSwallowDeliveryFailuresToAvoidAccountEnumeration() {
        User user = localUser();
        when(userRepository.findByEmail("alice@example.test")).thenReturn(Optional.of(user));
        doThrow(new EmailVerificationDeliveryException(new IllegalStateException("smtp")))
                .when(emailVerificationService)
                .issueChallenge(anyString(), any());

        assertThatCode(() -> service.requestReset("alice@example.test"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldChangePasswordAndRevokeAllSessionsWhenCodeIsValid() {
        User user = localUser();
        user.setPassword("old-hash");
        when(emailVerificationService.verifyChallenge(
                "alice@example.test", EmailVerificationPurpose.PASSWORD_RESET, "123456"))
                .thenReturn(EmailVerificationChallengeManager.VerificationResult.VERIFIED);
        when(userRepository.findByEmailForUpdate("alice@example.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NuevaClave123!")).thenReturn("new-hash");

        var result = service.confirmReset("alice@example.test", "123456", "NuevaClave123!");

        assertThat(result).isEqualTo(EmailVerificationChallengeManager.VerificationResult.VERIFIED);
        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userRepository).save(user);
        verify(authTokenService).revokeAllForUser(user.getId());
    }

    @Test
    void shouldNotChangePasswordWhenCodeIsInvalid() {
        when(emailVerificationService.verifyChallenge(
                "alice@example.test", EmailVerificationPurpose.PASSWORD_RESET, "000000"))
                .thenReturn(EmailVerificationChallengeManager.VerificationResult.INVALID_CODE);

        var result = service.confirmReset("alice@example.test", "000000", "NuevaClave123!");

        assertThat(result).isEqualTo(EmailVerificationChallengeManager.VerificationResult.INVALID_CODE);
        verifyNoInteractions(userRepository, passwordEncoder, authTokenService);
    }

    @Test
    void shouldRejectValidCodeWhenAccountIsNoLongerEligible() {
        User user = localUser();
        user.setAuthProvider(AuthProvider.GOOGLE);
        when(emailVerificationService.verifyChallenge(
                "alice@example.test", EmailVerificationPurpose.PASSWORD_RESET, "123456"))
                .thenReturn(EmailVerificationChallengeManager.VerificationResult.VERIFIED);
        when(userRepository.findByEmailForUpdate("alice@example.test")).thenReturn(Optional.of(user));

        var result = service.confirmReset("alice@example.test", "123456", "NuevaClave123!");

        assertThat(result).isEqualTo(EmailVerificationChallengeManager.VerificationResult.INVALID_CODE);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, authTokenService);
    }

    @Test
    void shouldNormalizeEmailBeforeConfirming() {
        when(emailVerificationService.verifyChallenge(
                "alice@example.test", EmailVerificationPurpose.PASSWORD_RESET, "123456"))
                .thenReturn(EmailVerificationChallengeManager.VerificationResult.EXPIRED);

        var result = service.confirmReset(" Alice@Example.test ", "123456", "NuevaClave123!");

        assertThat(result).isEqualTo(EmailVerificationChallengeManager.VerificationResult.EXPIRED);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailVerificationService).verifyChallenge(
                emailCaptor.capture(), any(), anyString());
        assertThat(emailCaptor.getValue()).isEqualTo("alice@example.test");
    }

    private User localUser() {
        User user = new User();
        user.setId(17L);
        user.setEmail("alice@example.test");
        user.setPassword("old-hash");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        user.setState(UserState.ENABLED);
        user.setRole(Role.USER);
        return user;
    }
}
