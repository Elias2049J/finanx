package com.elias.mismonedas.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public interface ExchangePort {
    BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency);

    BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date);

    Map<String, BigDecimal> getExchangeRates(String baseCurrency);
}
