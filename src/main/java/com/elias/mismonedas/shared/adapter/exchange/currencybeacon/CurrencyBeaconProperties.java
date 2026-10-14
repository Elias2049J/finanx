package com.elias.mismonedas.shared.adapter.exchange.currencybeacon;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.exchange.currencybeacon")
public record CurrencyBeaconProperties(
        String apiUrl,
        String apiKey
) { }
