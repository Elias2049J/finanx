package com.elias.mismonedas.shared.money;

import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.port.ExchangePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;








@Slf4j
@Component
@RequiredArgsConstructor
public class CurrencyConverter {

    private static final int MONEY_SCALE = 2;

    private final ExchangePort exchangePort;






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
