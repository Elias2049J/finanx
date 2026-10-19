package com.elias.mismonedas.shared.adapter.exchange.currencybeacon;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class ExchangeRateProvider {

    private final CurrencyBeaconClient client;

    public ExchangeRateProvider(CurrencyBeaconClient client) {
        this.client = client;
    }

    @Cacheable(value = "exchangeRates", key = "'rates_' + #baseCurrency")
    public Map<String, BigDecimal> rates(String baseCurrency) {
        return Map.copyOf(client.getLatestRates(baseCurrency));
    }
}
