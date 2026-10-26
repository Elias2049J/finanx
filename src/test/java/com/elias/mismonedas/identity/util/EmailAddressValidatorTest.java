package com.elias.mismonedas.identity.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressValidatorTest {

    @Test
    void shouldNormalizeASingleStrictEmailAddress() {
        assertThat(EmailAddressValidator.normalize(" Alice@Example.test "))
                .isEqualTo("alice@example.test");
    }

    @Test
    void shouldRejectRecipientListsAndDisplayNames() {
        assertThatThrownBy(() -> EmailAddressValidator.normalize("alice@example.test,bob@example.test"))
                .isInstanceOf(InvalidEmailAddressException.class);
        assertThatThrownBy(() -> EmailAddressValidator.normalize("Alice <alice@example.test>"))
                .isInstanceOf(InvalidEmailAddressException.class);
    }

    @Test
    void shouldRejectBlankOrOversizedAddresses() {
        assertThatThrownBy(() -> EmailAddressValidator.normalize("   "))
                .isInstanceOf(InvalidEmailAddressException.class);
        assertThatThrownBy(() -> EmailAddressValidator.normalize("a".repeat(321) + "@example.test"))
                .isInstanceOf(InvalidEmailAddressException.class);
    }
}
