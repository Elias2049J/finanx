package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import com.elias.mismonedas.identity.util.InvalidEmailAddressException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {

    private static final String HASH_SECRET = "a-test-only-secret-with-at-least-32-bytes";

    private final EmailVerificationChallengeManager challengeManager = mock(EmailVerificationChallengeManager.class);
    private final EmailVerificationEmailSender emailSender = mock(EmailVerificationEmailSender.class);
    private final SecureRandom secureRandom = new SecureRandom() {
        @Override
        public int nextInt(int bound) {
            return 42;
        }
    };
    private final EmailVerificationCodeHasher codeHasher = new EmailVerificationCodeHasher(HASH_SECRET);

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(
                challengeManager, codeHasher, emailSender, secureRandom);
        when(challengeManager.persistChallenge(anyString(), any(), anyString())).thenReturn(73L);
    }

    @Test
    void shouldPersistTheHashedCodeBeforeSendingTheSixDigitValue() {
        var order = inOrder(challengeManager, emailSender);

        service.issueChallenge(" Alice@Example.test ", EmailVerificationPurpose.REGISTRATION);

        var hashCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        order.verify(challengeManager).persistChallenge(
                eq("alice@example.test"),
                eq(EmailVerificationPurpose.REGISTRATION),
                hashCaptor.capture());
        order.verify(emailSender).sendVerificationCode(
                "alice@example.test", "000042", Duration.ofMinutes(15));
        assertThat(codeHasher.matches(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION, "000042", hashCaptor.getValue()))
                .isTrue();
    }

    @Test
    void shouldInvalidateOnlyTheIssuedChallengeWhenEmailDeliveryFails() {
        doThrow(new IllegalStateException("smtp failure"))
                .when(emailSender).sendVerificationCode(anyString(), anyString(), any());

        assertThatThrownBy(() -> service.issueChallenge("alice@example.test", EmailVerificationPurpose.REGISTRATION))
                .isInstanceOf(EmailVerificationDeliveryException.class);

        var hashCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(challengeManager).persistChallenge(
                eq("alice@example.test"), eq(EmailVerificationPurpose.REGISTRATION), hashCaptor.capture());
        verify(challengeManager).invalidateIfCurrent(73L, hashCaptor.getValue());
    }

    @Test
    void shouldRejectMultipleRecipientAddressesBeforePersistingOrSending() {
        assertThatThrownBy(() -> service.issueChallenge(
                "alice@example.test,bob@example.test", EmailVerificationPurpose.REGISTRATION))
                .isInstanceOf(InvalidEmailAddressException.class);

        verifyNoInteractions(challengeManager, emailSender);
    }

    @Test
    void shouldNotQueryTheChallengeForMalformedCodes() {
        var result = service.verifyChallenge(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION, "12345x");

        assertThat(result).isEqualTo(EmailVerificationChallengeManager.VerificationResult.INVALID_CODE);
        verifyNoInteractions(challengeManager);
    }

    @Test
    void shouldNormalizeAddressBeforeVerification() {
        when(challengeManager.verifyChallenge(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION, "123456"))
                .thenReturn(EmailVerificationChallengeManager.VerificationResult.VERIFIED);

        var result = service.verifyChallenge(
                " Alice@Example.test ", EmailVerificationPurpose.REGISTRATION, "123456");

        assertThat(result).isEqualTo(EmailVerificationChallengeManager.VerificationResult.VERIFIED);
        verify(challengeManager).verifyChallenge(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION, "123456");
    }
}
