package com.elias.mismonedas.identity.repository;

import com.elias.mismonedas.identity.domain.entity.EmailVerificationChallenge;
import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.messaging.rabbitmq.consumer-auto-startup=false")
@Transactional
class EmailVerificationChallengeRepositoryTest {

    @Autowired
    private EmailVerificationChallengeRepository repository;

    @Test
    void shouldPersistChallengeAndDeleteItOnceExpired() {
        OffsetDateTime now = OffsetDateTime.now();
        EmailVerificationChallenge challenge = EmailVerificationChallenge.builder()
                .email("alice-" + UUID.randomUUID() + "@example.test")
                .purpose(EmailVerificationPurpose.REGISTRATION)
                .codeHash("hashed-code")
                .expiresAt(now.plusMinutes(15))
                .failedAttempts(0)
                .lastSentAt(now)
                .createdAt(now)
                .build();

        EmailVerificationChallenge saved = repository.saveAndFlush(challenge);

        assertThat(repository.findByEmailAndPurposeForUpdate(
                saved.getEmail(), EmailVerificationPurpose.REGISTRATION)).contains(saved);
        assertThat(repository.deleteExpiredAtOrBefore(now)).isZero();

        saved.setExpiresAt(now);
        repository.saveAndFlush(saved);

        assertThat(repository.deleteExpiredAtOrBefore(now)).isEqualTo(1);
    }
}
