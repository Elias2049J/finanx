package com.elias.mismonedas.shared.adapter.exchange.currencybeacon;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
public class CurrencyBeaconClient {

    private final RestClient restClient;
    private final CurrencyBeaconProperties properties;

    public CurrencyBeaconClient(CurrencyBeaconProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.apiUrl())
                .build();
    }

    @CircuitBreaker(name = "currencybeacon")
    @Retry(name = "currencybeacon")
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        var response = restClient.get()
                .uri("/convert?api_key={key}&from={from}&to={to}&amount={amount}",
                        properties.apiKey(), fromCurrency, toCurrency, amount)
                .retrieve()
                .body(CurrencyBeaconConvertResponse.class);

        if (response == null || response.response() == null || response.response().value() == null) {
            throw new IllegalStateException("CurrencyBeacon devolvio una respuesta de conversion vacia");
        }
        return response.response().value().setScale(2, RoundingMode.HALF_UP);
    }

    @CircuitBreaker(name = "currencybeacon")
    @Retry(name = "currencybeacon")
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
        var response = restClient.get()
                .uri("/convert?api_key={key}&from={from}&to={to}&amount={amount}&date={date}",
                        properties.apiKey(), fromCurrency, toCurrency, amount,
                        date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .retrieve()
                .body(CurrencyBeaconConvertResponse.class);

        if (response == null || response.response() == null || response.response().value() == null) {
            throw new IllegalStateException("CurrencyBeacon devolvio una respuesta de conversion vacia");
        }
        return response.response().value().setScale(2, RoundingMode.HALF_UP);
    }

    @CircuitBreaker(name = "currencybeacon")
    @Retry(name = "currencybeacon")
    public Map<String, BigDecimal> getLatestRates(String baseCurrency) {
        var response = restClient.get()
                .uri("/latest?api_key={key}&base={base}", properties.apiKey(), baseCurrency)
                .retrieve()
                .body(CurrencyBeaconResponse.class);

        if (response == null || response.response() == null || response.response().rates() == null) {
            throw new IllegalStateException("CurrencyBeacon devolvio tasas vacias");
        }
        log.debug("Fetched exchange rates for base={}", baseCurrency);
        return response.response().rates();
    }

    record CurrencyBeaconResponse(Meta meta, ResponseData response) {
        record Meta(Integer code) {}
        record ResponseData(String date, String base, Map<String, BigDecimal> rates) {}
    }

    record CurrencyBeaconConvertResponse(Meta meta, ConvertResponseData response) {
        record Meta(Integer code) {}
        record ConvertResponseData(Long timestamp, String date, String from, String to, BigDecimal amount, BigDecimal value) {}
    }
}
