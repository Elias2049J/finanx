package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.entity.EmailVerificationChallenge;
import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import com.elias.mismonedas.identity.repository.EmailVerificationChallengeLock;
import com.elias.mismonedas.identity.repository.EmailVerificationChallengeRepository;
import io.github.resilience4j.ratelimiter.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailVerificationChallengeManagerTest {

    private static final Instant NOW = Instant.parse("2026-10-26T17:00:00Z");

    private final EmailVerificationChallengeRepository challengeRepository =
            mock(EmailVerificationChallengeRepository.class);
    private final EmailVerificationChallengeLock challengeLock = mock(EmailVerificationChallengeLock.class);
    private final EmailVerificationCodeHasher codeHasher = mock(EmailVerificationCodeHasher.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private EmailVerificationChallengeManager manager;

    @BeforeEach
    void setUp() {
        when(rateLimiter.acquirePermission()).thenReturn(true);
        manager = new EmailVerificationChallengeManager(
                challengeRepository, challengeLock, codeHasher, rateLimiter, clock);
    }

    @Test
    void shouldPersistChallengeOnlyAfterTakingThePerEmailLockAndGlobalPermit() {
        when(challengeRepository.findByEmailAndPurposeForUpdate(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION)).thenReturn(Optional.empty());
        when(challengeRepository.saveAndFlush(any(EmailVerificationChallenge.class)))
                .thenReturn(EmailVerificationChallenge.builder().id(73L).build());

        Long id = manager.persistChallenge(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION, "hmac-hash");

        assertThat(id).isEqualTo(73L);
        var order = inOrder(challengeLock, challengeRepository, rateLimiter);
        order.verify(challengeLock).setLocalLockTimeout();
        order.verify(challengeLock).acquire("REGISTRATION|alice@example.test");
        order.verify(challengeRepository).findByEmailAndPurposeForUpdate(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION);
        order.verify(rateLimiter).acquirePermission();
        order.verify(challengeRepository).saveAndFlush(any(EmailVerificationChallenge.class));
    }

    @Test
    void shouldRetainCooldownWhenAChallengeIsAlreadyExhausted() {
        EmailVerificationChallenge exhausted = challenge(
                OffsetDateTime.now(clock).minusSeconds(30), 5);
        when(challengeRepository.findByEmailAndPurposeForUpdate(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION)).thenReturn(Optional.of(exhausted));

        assertThatThrownBy(() -> manager.persistChallenge(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION, "new-hash"))
                .isInstanceOf(VerificationCodeCooldownException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(30L);

        verify(rateLimiter, never()).acquirePermission();
        verify(challengeRepository, never()).saveAndFlush(any(EmailVerificationChallenge.class));
    }

    @Test
    void shouldNotPersistWhenTheGlobalEmailLimitIsReached() {
        when(challengeRepository.findByEmailAndPurposeForUpdate(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION)).thenReturn(Optional.empty());
        when(rateLimiter.acquirePermission()).thenReturn(false);

        assertThatThrownBy(() -> manager.persistChallenge(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION, "hmac-hash"))
                .isInstanceOf(VerificationCodeRateLimitException.class);

        verify(challengeRepository, never()).saveAndFlush(any(EmailVerificationChallenge.class));
    }

    @Test
    void shouldPersistTheFifthWrongAttemptAndKeepTheChallengeBlocked() {
        EmailVerificationChallenge existing = challenge(OffsetDateTime.now(clock), 4);
        when(challengeRepository.findByEmailAndPurposeForUpdate(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION)).thenReturn(Optional.of(existing));
        when(codeHasher.matches(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION, "123456", "hmac-hash"))
                .thenReturn(false);

        EmailVerificationChallengeManager.VerificationResult result = manager.verifyChallenge(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION, "123456");

        assertThat(result).isEqualTo(EmailVerificationChallengeManager.VerificationResult.ATTEMPTS_EXCEEDED);
        assertThat(existing.getFailedAttempts()).isEqualTo(5);
        verify(challengeRepository).save(existing);
        verify(challengeRepository, never()).delete(existing);
    }

    @Test
    void shouldNotHashOrDeleteAChallengeThatHasAlreadyReachedTheAttemptLimit() {
        EmailVerificationChallenge exhausted = challenge(OffsetDateTime.now(clock), 5);
        when(challengeRepository.findByEmailAndPurposeForUpdate(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION)).thenReturn(Optional.of(exhausted));

        EmailVerificationChallengeManager.VerificationResult result = manager.verifyChallenge(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION, "123456");

        assertThat(result).isEqualTo(EmailVerificationChallengeManager.VerificationResult.ATTEMPTS_EXCEEDED);
        verifyNoInteractions(codeHasher);
        verify(challengeRepository, never()).delete(exhausted);
    }

    @Test
    void shouldRejectMalformedCodeWithoutReadingTheChallenge() {
        EmailVerificationChallengeManager.VerificationResult result = manager.verifyChallenge(
                "alice@example.test", EmailVerificationPurpose.REGISTRATION, "12345x");

        assertThat(result).isEqualTo(EmailVerificationChallengeManager.VerificationResult.INVALID_CODE);
        verifyNoInteractions(challengeRepository, challengeLock, codeHasher);
    }

    @Test
    void shouldDeleteOnlyTheChallengeThatMatchesTheFailedDelivery() {
        EmailVerificationChallenge current = challenge(OffsetDateTime.now(clock), 0);
        when(challengeRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(current));

        manager.invalidateIfCurrent(41L, "stale-hash");
        manager.invalidateIfCurrent(41L, "hmac-hash");

        verify(challengeRepository, times(1)).delete(current);
    }

    private EmailVerificationChallenge challenge(OffsetDateTime lastSentAt, int failedAttempts) {
        return EmailVerificationChallenge.builder()
                .id(41L)
                .email("alice@example.test")
                .purpose(EmailVerificationPurpose.REGISTRATION)
                .codeHash("hmac-hash")
                .expiresAt(OffsetDateTime.now(clock).plusMinutes(15))
                .failedAttempts(failedAttempts)
                .lastSentAt(lastSentAt)
                .createdAt(OffsetDateTime.now(clock))
                .build();
    }
}
