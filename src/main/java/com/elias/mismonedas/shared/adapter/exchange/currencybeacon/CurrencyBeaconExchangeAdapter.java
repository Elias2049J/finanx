package com.elias.mismonedas.shared.adapter.exchange.currencybeacon;

import com.elias.mismonedas.shared.exception.CurrencyConversionException;
import com.elias.mismonedas.shared.port.ExchangePort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

public class CurrencyBeaconExchangeAdapter implements ExchangePort {

    private final CurrencyBeaconClient client;
    private final ExchangeRateProvider rateProvider;

    public CurrencyBeaconExchangeAdapter(CurrencyBeaconClient client, ExchangeRateProvider rateProvider) {
        this.client = client;
        this.rateProvider = rateProvider;
    }

    @Override
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        String from = fromCurrency.toUpperCase(Locale.ROOT);
        String to = toCurrency.toUpperCase(Locale.ROOT);
        if (from.equals(to)) {
            return amount;
        }
        BigDecimal rate = rates(from).get(to);
        if (rate == null) {
            throw new CurrencyConversionException("No exchange rate available for " + from + " to " + to);
        }
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }
        try {
            return client.convert(amount, fromCurrency, toCurrency, date);
        } catch (Exception e) {
            throw new CurrencyConversionException(
                    "Failed to convert " + fromCurrency + " to " + toCurrency + " on " + date, e);
        }
    }

    @Override
    public Map<String, BigDecimal> getExchangeRates(String baseCurrency) {
        return rates(baseCurrency.toUpperCase(Locale.ROOT));
    }

    private Map<String, BigDecimal> rates(String baseCurrency) {
        try {
            return rateProvider.rates(baseCurrency);
        } catch (Exception e) {
            throw new CurrencyConversionException("Failed to fetch exchange rates for " + baseCurrency, e);
        }
    }
}
