package com.elias.mismonedas.shared.domain.vo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void shouldAddSameCurrencyPreservingScale() {
        Money result = Money.of(new BigDecimal("10.00"), "PEN")
                .add(Money.of(new BigDecimal("5.50"), "PEN"));

        assertThat(result.amount()).isEqualByComparingTo("15.50");
        assertThat(result.currencyCode()).isEqualTo("PEN");
    }

    @Test
    void shouldSubtractAndGoNegative() {
        Money result = Money.of(new BigDecimal("10.00"), "PEN")
                .subtract(Money.of(new BigDecimal("25.00"), "PEN"));

        assertThat(result.amount()).isEqualByComparingTo("-15.00");
    }

    @Test
    void shouldAllowConstructingNegativeAmounts() {
        Money result = Money.of(new BigDecimal("-3.25"), "USD");

        assertThat(result.amount()).isEqualByComparingTo("-3.25");
    }

    @Test
    void shouldMultiply() {
        Money result = Money.of(new BigDecimal("10.00"), "PEN")
                .multiply(new BigDecimal("0.20"));

        assertThat(result.amount()).isEqualByComparingTo("2.0000");
    }

    @Test
    void shouldRejectOperatingOnDifferentCurrencies() {
        Money pen = Money.of(new BigDecimal("10.00"), "PEN");
        Money usd = Money.of(new BigDecimal("10.00"), "USD");

        assertThatThrownBy(() -> pen.add(usd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pen.subtract(usd)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldMatchCurrencyIgnoringCase() {
        Money result = Money.of(new BigDecimal("10.00"), "pen")
                .add(Money.of(new BigDecimal("1.00"), "PEN"));

        assertThat(result.amount()).isEqualByComparingTo("11.00");
    }
}
