package com.elias.mismonedas.identity.domain.entity;

import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationChallengeTest {

    @Test
    void shouldExpireAtTheExactExpirationInstant() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-26T12:00:00Z");
        EmailVerificationChallenge challenge = challenge(now.plusMinutes(15));

        assertThat(challenge.isExpiredAt(now)).isFalse();
        assertThat(challenge.isExpiredAt(now.plusMinutes(15))).isTrue();
    }

    @Test
    void shouldResetFailedAttemptsWhenCodeIsReplaced() {
        EmailVerificationChallenge challenge = challenge(OffsetDateTime.now().plusMinutes(15));
        challenge.recordFailedAttempt();
        challenge.recordFailedAttempt();

        OffsetDateTime resentAt = OffsetDateTime.now();
        challenge.replaceCode("new-hash", resentAt, resentAt.plusMinutes(15));

        assertThat(challenge.getFailedAttempts()).isZero();
        assertThat(challenge.getCodeHash()).isEqualTo("new-hash");
        assertThat(challenge.getLastSentAt()).isEqualTo(resentAt);
        assertThat(challenge.getExpiresAt()).isEqualTo(resentAt.plusMinutes(15));
    }

    private EmailVerificationChallenge challenge(OffsetDateTime expiresAt) {
        return EmailVerificationChallenge.builder()
                .email("alice@example.test")
                .purpose(EmailVerificationPurpose.REGISTRATION)
                .codeHash("hashed-code")
                .expiresAt(expiresAt)
                .failedAttempts(0)
                .lastSentAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
