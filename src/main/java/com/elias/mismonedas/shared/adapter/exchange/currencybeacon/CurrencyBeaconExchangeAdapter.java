package com.elias.mismonedas.shared.adapter.exchange.currencybeacon;

import com.elias.mismonedas.shared.port.ExchangePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter de {@link ExchangePort} que envuelve a {@link CurrencyBeaconClient}
 * con caché ({@code @Cacheable}) y fallback explícito a tasas en caché.
 *
 * <p>La resiliencia (circuit breaker / reintentos) vive en el cliente; aquí solo
 * se traduce un fallo remoto en un valor servido desde caché para no romper el
 * flujo de negocio.</p>
 */
@Slf4j
public class CurrencyBeaconExchangeAdapter implements ExchangePort {

    private final CurrencyBeaconClient client;

    public CurrencyBeaconExchangeAdapter(CurrencyBeaconClient client) {
        this.client = client;
    }

    @Override
    @Cacheable(value = "exchangeRates", key = "'convert_' + #fromCurrency + '_' + #toCurrency")
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }
        try {
            return client.convert(amount, fromCurrency, toCurrency);
        } catch (Exception e) {
            log.warn("Fallo al convertir {} de {} a {}, usando tasas en cache: {}",
                    amount, fromCurrency, toCurrency, e.getMessage());
            return fallbackConvert(amount, fromCurrency, toCurrency);
        }
    }

    @Override
    @Cacheable(value = "exchangeRates", key = "'convert_' + #fromCurrency + '_' + #toCurrency + '_' + #date")
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }
        try {
            return client.convert(amount, fromCurrency, toCurrency, date);
        } catch (Exception e) {
            log.warn("Fallo al convertir {} de {} a {} en {}, usando tasas en cache: {}",
                    amount, fromCurrency, toCurrency, date, e.getMessage());
            return fallbackConvert(amount, fromCurrency, toCurrency);
        }
    }

    @Override
    @Cacheable(value = "exchangeRates", key = "'rates_' + #baseCurrency")
    public Map<String, BigDecimal> getExchangeRates(String baseCurrency) {
        try {
            return client.getLatestRates(baseCurrency);
        } catch (Exception e) {
            log.warn("Fallo al obtener tasas para base={}, usando cache fallback: {}", baseCurrency, e.getMessage());
        }
        return Map.of();
    }

    private BigDecimal fallbackConvert(BigDecimal amount, String from, String to) {
        Map<String, BigDecimal> rates = getExchangeRates(from);
        return Optional.ofNullable(rates.get(to.toUpperCase()))
                .map(rate -> amount.multiply(rate).setScale(2, RoundingMode.HALF_UP))
                .orElse(amount);
    }
}
