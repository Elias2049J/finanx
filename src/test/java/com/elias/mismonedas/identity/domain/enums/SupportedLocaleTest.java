package com.elias.mismonedas.identity.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportedLocaleTest {

    @Test
    void shouldCanonicalizeSupportedLocaleTags() {
        assertThat(SupportedLocale.canonicalize("EN-us")).isEqualTo("en-US");
        assertThat(SupportedLocale.canonicalize("es-pe")).isEqualTo("es-PE");
    }

    @Test
    void shouldRejectUnsupportedLocaleTags() {
        assertThatThrownBy(() -> SupportedLocale.canonicalize("fr-FR"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
