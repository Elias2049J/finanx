package com.elias.mismonedas.identity.repository;

import com.elias.mismonedas.identity.domain.entity.EmailVerificationChallenge;
import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.messaging.rabbitmq.consumer-auto-startup=false")
class EmailVerificationChallengeRepositoryTest {

    @Autowired
    private EmailVerificationChallengeRepository repository;

    private String email;

    @AfterEach
    void deleteTestChallenge() {
        if (email != null) {
            repository.deleteByEmailAndPurpose(email, EmailVerificationPurpose.REGISTRATION);
            repository.deleteByEmailAndPurpose(email, EmailVerificationPurpose.EXISTING_ACCOUNT);
        }
    }

    @Test
    void shouldPersistChallengeAndDeleteItOnceExpired() {
        OffsetDateTime now = OffsetDateTime.now();
        email = "alice-" + UUID.randomUUID() + "@example.test";
        EmailVerificationChallenge challenge = EmailVerificationChallenge.builder()
                .email(email)
                .purpose(EmailVerificationPurpose.REGISTRATION)
                .codeHash("hashed-code")
                .expiresAt(now.plusMinutes(15))
                .failedAttempts(0)
                .lastSentAt(now)
                .createdAt(now)
                .build();

        EmailVerificationChallenge saved = repository.saveAndFlush(challenge);

        assertThat(repository.findByEmailAndPurposeForUpdate(
                saved.getEmail(), EmailVerificationPurpose.REGISTRATION))
                .hasValueSatisfying(found -> assertThat(found.getEmail()).isEqualTo(email));
        assertThat(repository.deleteExpiredAtOrBefore(now)).isZero();

        repository.saveAndFlush(EmailVerificationChallenge.builder()
                .email(email)
                .purpose(EmailVerificationPurpose.EXISTING_ACCOUNT)
                .codeHash("expired-hash")
                .expiresAt(now)
                .failedAttempts(0)
                .lastSentAt(now.minusMinutes(15))
                .createdAt(now.minusMinutes(15))
                .build());

        assertThat(repository.deleteExpiredAtOrBefore(now)).isEqualTo(1);
    }
}
