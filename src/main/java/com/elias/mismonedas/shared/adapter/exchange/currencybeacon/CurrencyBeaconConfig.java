package com.elias.mismonedas.shared.adapter.exchange.currencybeacon;

import com.elias.mismonedas.shared.port.ExchangePort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(CurrencyBeaconProperties.class)
public class CurrencyBeaconConfig {

    @Bean
    public ExchangePort currencyBeaconExchangeAdapter(
            CurrencyBeaconProperties properties,
            RestClient.Builder restClientBuilder) {
        return new CurrencyBeaconExchangeAdapter(properties, restClientBuilder);
    }
}
