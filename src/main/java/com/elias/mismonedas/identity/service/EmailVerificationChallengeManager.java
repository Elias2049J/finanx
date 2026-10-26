package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.EmailVerificationPolicy;
import com.elias.mismonedas.identity.domain.entity.EmailVerificationChallenge;
import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import com.elias.mismonedas.identity.repository.EmailVerificationChallengeLock;
import com.elias.mismonedas.identity.repository.EmailVerificationChallengeRepository;
import io.github.resilience4j.ratelimiter.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailVerificationChallengeManager {

    private static final long GLOBAL_RATE_LIMIT_RETRY_AFTER_SECONDS = 60;

    private final EmailVerificationChallengeRepository challengeRepository;
    private final EmailVerificationChallengeLock challengeLock;
    private final EmailVerificationCodeHasher codeHasher;
    private final RateLimiter emailVerificationRateLimiter;
    private final Clock clock;

    @Transactional
    public Long persistChallenge(String email, EmailVerificationPurpose purpose, String codeHash) {
        challengeLock.setLocalLockTimeout();
        challengeLock.acquire(purpose.name() + "|" + email);

        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<EmailVerificationChallenge> existing =
                challengeRepository.findByEmailAndPurposeForUpdate(email, purpose);
        if (existing.isPresent()) {
            OffsetDateTime nextAllowedAt = existing.get().getLastSentAt()
                    .plus(EmailVerificationPolicy.RESEND_COOLDOWN);
            if (nextAllowedAt.isAfter(now)) {
                long retryAfterSeconds = roundSecondsUp(Duration.between(now, nextAllowedAt));
                throw new VerificationCodeCooldownException(retryAfterSeconds);
            }
        }

        if (!emailVerificationRateLimiter.acquirePermission()) {
            throw new VerificationCodeRateLimitException(GLOBAL_RATE_LIMIT_RETRY_AFTER_SECONDS);
        }

        OffsetDateTime expiresAt = now.plus(EmailVerificationPolicy.CODE_TTL);
        EmailVerificationChallenge challenge = existing.orElseGet(() -> EmailVerificationChallenge.builder()
                .email(email)
                .purpose(purpose)
                .createdAt(now)
                .build());
        challenge.replaceCode(codeHash, now, expiresAt);
        return challengeRepository.saveAndFlush(challenge).getId();
    }

    @Transactional
    public VerificationResult verifyChallenge(String email, EmailVerificationPurpose purpose, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return VerificationResult.INVALID_CODE;
        }

        challengeLock.setLocalLockTimeout();
        Optional<EmailVerificationChallenge> optionalChallenge =
                challengeRepository.findByEmailAndPurposeForUpdate(email, purpose);
        if (optionalChallenge.isEmpty()) {
            return VerificationResult.INVALID_CODE;
        }

        EmailVerificationChallenge challenge = optionalChallenge.get();
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (challenge.isExpiredAt(now)) {
            challengeRepository.delete(challenge);
            return VerificationResult.EXPIRED;
        }
        if (challenge.hasReachedAttemptLimit()) {
            return VerificationResult.ATTEMPTS_EXCEEDED;
        }
        if (!codeHasher.matches(email, purpose, code, challenge.getCodeHash())) {
            challenge.recordFailedAttempt();
            challengeRepository.save(challenge);
            return challenge.hasReachedAttemptLimit()
                    ? VerificationResult.ATTEMPTS_EXCEEDED
                    : VerificationResult.INVALID_CODE;
        }

        challengeRepository.delete(challenge);
        return VerificationResult.VERIFIED;
    }

    @Transactional
    public void invalidateIfCurrent(Long challengeId, String codeHash) {
        if (challengeId == null) {
            return;
        }
        challengeLock.setLocalLockTimeout();
        challengeRepository.findByIdForUpdate(challengeId)
                .filter(challenge -> codeHash.equals(challenge.getCodeHash()))
                .ifPresent(challengeRepository::delete);
    }

    private long roundSecondsUp(Duration duration) {
        long nanos = duration.toNanos();
        return Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
    }

    public enum VerificationResult {
        VERIFIED,
        INVALID_CODE,
        EXPIRED,
        ATTEMPTS_EXCEEDED
    }
}
