package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.entity.EmailVerificationChallenge;
import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import com.elias.mismonedas.identity.repository.EmailVerificationChallengeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.messaging.rabbitmq.consumer-auto-startup=false",
        "spring.task.scheduling.enabled=false"
})
class EmailVerificationChallengeConcurrencyTest {

    @Autowired private EmailVerificationChallengeManager manager;
    @Autowired private EmailVerificationChallengeRepository challengeRepository;
    @Autowired private EmailVerificationCodeHasher codeHasher;

    private String email;

    @AfterEach
    void deleteChallenge() {
        if (email != null) {
            challengeRepository.deleteByEmailAndPurpose(email, EmailVerificationPurpose.REGISTRATION);
        }
    }

    @Test
    void concurrentFirstIssuesForOneEmailProduceOneChallengeAndOneCooldown() throws Exception {
        email = "first-issue-" + UUID.randomUUID() + "@example.test";
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> issueWhenReleased(start, "first-hash"));
            Future<String> second = executor.submit(() -> issueWhenReleased(start, "second-hash"));

            start.countDown();
            List<String> outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes).containsExactlyInAnyOrder("ISSUED", "COOLDOWN");
            assertThat(challengeRepository.findByEmailAndPurposeForUpdate(
                    email, EmailVerificationPurpose.REGISTRATION)).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentVerificationConsumesTheCodeAtMostOnce() throws Exception {
        email = "verify-race-" + UUID.randomUUID() + "@example.test";
        String code = "004281";
        OffsetDateTime now = OffsetDateTime.now();
        EmailVerificationChallenge challenge = EmailVerificationChallenge.builder()
                .email(email)
                .purpose(EmailVerificationPurpose.REGISTRATION)
                .codeHash(codeHasher.hash(email, EmailVerificationPurpose.REGISTRATION, code))
                .expiresAt(now.plusMinutes(15))
                .failedAttempts(0)
                .lastSentAt(now)
                .createdAt(now)
                .build();
        challengeRepository.saveAndFlush(challenge);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<EmailVerificationChallengeManager.VerificationResult> first = executor.submit(
                    () -> verifyWhenReleased(start, code));
            Future<EmailVerificationChallengeManager.VerificationResult> second = executor.submit(
                    () -> verifyWhenReleased(start, code));

            start.countDown();
            List<EmailVerificationChallengeManager.VerificationResult> outcomes = List.of(
                    first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes).containsExactlyInAnyOrder(
                    EmailVerificationChallengeManager.VerificationResult.VERIFIED,
                    EmailVerificationChallengeManager.VerificationResult.INVALID_CODE);
            assertThat(challengeRepository.findByEmailAndPurposeForUpdate(
                    email, EmailVerificationPurpose.REGISTRATION)).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    private String issueWhenReleased(CountDownLatch start, String codeHash) throws InterruptedException {
        start.await();
        try {
            manager.persistChallenge(email, EmailVerificationPurpose.REGISTRATION, codeHash);
            return "ISSUED";
        } catch (VerificationCodeCooldownException expected) {
            return "COOLDOWN";
        }
    }

    private EmailVerificationChallengeManager.VerificationResult verifyWhenReleased(
            CountDownLatch start,
            String code) throws InterruptedException {
        start.await();
        return manager.verifyChallenge(email, EmailVerificationPurpose.REGISTRATION, code);
    }
}
