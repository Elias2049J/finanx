package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationCodeHasherTest {

    private final EmailVerificationCodeHasher hasher =
            new EmailVerificationCodeHasher("a-test-only-secret-with-at-least-32-bytes");

    @Test
    void shouldMatchOnlyTheSameEmailPurposeAndCode() {
        String hash = hasher.hash("alice@example.test", EmailVerificationPurpose.REGISTRATION, "004281");

        assertThat(hash).hasSize(64).doesNotContain("004281");
        assertThat(hasher.matches("alice@example.test", EmailVerificationPurpose.REGISTRATION, "004281", hash))
                .isTrue();
        assertThat(hasher.matches("bob@example.test", EmailVerificationPurpose.REGISTRATION, "004281", hash))
                .isFalse();
        assertThat(hasher.matches("alice@example.test", EmailVerificationPurpose.EXISTING_ACCOUNT, "004281", hash))
                .isFalse();
        assertThat(hasher.matches("alice@example.test", EmailVerificationPurpose.REGISTRATION, "004282", hash))
                .isFalse();
    }

    @Test
    void shouldRequireAnApplicationSecretWithAtLeastThirtyTwoBytes() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new EmailVerificationCodeHasher("short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
