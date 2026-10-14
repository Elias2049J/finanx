package com.elias.mismonedas.shared.money;

import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.port.ExchangePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Centralizes currency conversion for the application.
 *
 * <p>Single home for the previously duplicated pattern:
 * "if the currency differs convert it, otherwise use the raw amount; if the
 * conversion fails fall back to the raw amount". The rounding of converted
 * values ({@code HALF_UP}, scale 2) also lives here.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrencyConverter {

    private static final int MONEY_SCALE = 2;

    private final ExchangePort exchangePort;

    /**
     * Converts {@code amount} to {@code toCurrency}. Amounts already in the
     * target currency (or with a null/unknown currency) are returned unchanged,
     * preserving their original scale. On conversion failure the raw amount is
     * kept, relabeled with the target currency, so a sum never breaks.
     */
    public Money convert(Money amount, String toCurrency) {
        if (amount == null) {
            return null;
        }

        String fromCurrency = amount.currencyCode();
        if (fromCurrency == null || toCurrency == null || fromCurrency.equalsIgnoreCase(toCurrency)) {
            return toCurrency == null ? amount : new Money(amount.amount(), toCurrency);
        }

        try {
            BigDecimal converted = exchangePort.convert(amount.amount(), fromCurrency, toCurrency);
            if (converted == null) {
                return new Money(amount.amount(), toCurrency);
            }
            return new Money(converted.setScale(MONEY_SCALE, RoundingMode.HALF_UP), toCurrency);
        } catch (Exception e) {
            log.warn("Exchange conversion failed from {} to {}, using raw amount", fromCurrency, toCurrency, e);
            return new Money(amount.amount(), toCurrency);
        }
    }
}
