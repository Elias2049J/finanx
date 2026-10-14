package com.elias.mismonedas.shared.domain.vo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object for a monetary amount tied to a currency code.
 *
 * <p>Negative amounts are allowed on purpose: wallets subtract balances and
 * budgets can run a deficit, so this VO must not forbid them.</p>
 */
public record Money(BigDecimal amount, String currencyCode) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyCode);
    }

    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, currencyCode);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currencyCode);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currencyCode);
    }

    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor must not be null");
        return new Money(amount.multiply(factor), currencyCode);
    }

    public Money negate() {
        return new Money(amount.negate(), currencyCode);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (currencyCode != null && other.currencyCode != null
                && !currencyCode.equalsIgnoreCase(other.currencyCode)) {
            throw new IllegalArgumentException(
                    "Cannot operate on different currencies: " + currencyCode + " vs " + other.currencyCode);
        }
    }
}
