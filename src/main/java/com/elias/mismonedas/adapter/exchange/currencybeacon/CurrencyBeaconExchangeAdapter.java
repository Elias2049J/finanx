package com.elias.mismonedas.adapter.exchange.currencybeacon;

import com.elias.mismonedas.port.ExchangePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class CurrencyBeaconExchangeAdapter implements ExchangePort {

    private final RestClient restClient;
    private final CurrencyBeaconProperties properties;

    public CurrencyBeaconExchangeAdapter(
            CurrencyBeaconProperties properties,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.apiUrl())
                .build();
    }

    @Override
    @Cacheable(value = "exchangeRates", key = "'convert_' + #fromCurrency + '_' + #toCurrency")
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }
        String url = "/convert?api_key={key}&from={from}&to={to}&amount={amount}";
        return callConvert(url, amount, fromCurrency, toCurrency);
    }

    @Override
    @Cacheable(value = "exchangeRates", key = "'convert_' + #fromCurrency + '_' + #toCurrency + '_' + #date")
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return amount;
        }
        String url = "/convert?api_key={key}&from={from}&to={to}&amount={amount}&date={date}";
        return callConvertWithDate(url, amount, fromCurrency, toCurrency, date);
    }

    @Override
    @Cacheable(value = "exchangeRates", key = "'rates_' + #baseCurrency")
    public Map<String, BigDecimal> getExchangeRates(String baseCurrency) {
        String url = "/latest?api_key={key}&base={base}";
        try {
            var response = restClient.get()
                    .uri(url, properties.apiKey(), baseCurrency)
                    .retrieve()
                    .body(CurrencyBeaconResponse.class);

            if (response != null && response.response() != null && response.response().rates() != null) {
                log.debug("Fetched exchange rates for base={}", baseCurrency);
                return response.response().rates();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch exchange rates for base={}, using cache fallback", baseCurrency, e);
        }
        return Map.of();
    }

    private BigDecimal callConvert(String url, BigDecimal amount, String from, String to) {
        try {
            var response = restClient.get()
                    .uri(url, properties.apiKey(), from, to, amount)
                    .retrieve()
                    .body(CurrencyBeaconConvertResponse.class);

            if (response != null && response.response() != null) {
                return response.response().value().setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.warn("Failed to convert {} from {} to {}, using cached rates as fallback", amount, from, to, e);
        }
        return fallbackConvert(amount, from, to);
    }

    private BigDecimal callConvertWithDate(String url, BigDecimal amount, String from, String to, LocalDate date) {
        try {
            var response = restClient.get()
                    .uri(url, properties.apiKey(), from, to, amount, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .retrieve()
                    .body(CurrencyBeaconConvertResponse.class);

            if (response != null && response.response() != null) {
                return response.response().value().setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.warn("Failed to convert historical {} from {} to {} on {}, using cached rates", amount, from, to, date, e);
        }
        return fallbackConvert(amount, from, to);
    }

    private BigDecimal fallbackConvert(BigDecimal amount, String from, String to) {
        Map<String, BigDecimal> rates = getExchangeRates(from);
        return Optional.ofNullable(rates.get(to.toUpperCase()))
                .map(rate -> amount.multiply(rate).setScale(2, RoundingMode.HALF_UP))
                .orElse(amount);
    }

    private record CurrencyBeaconResponse(Meta meta, ResponseData response) {
        private record Meta(Integer code) {}
        private record ResponseData(String date, String base, Map<String, BigDecimal> rates) {}
    }

    private record CurrencyBeaconConvertResponse(Meta meta, ConvertResponseData response) {
        private record Meta(Integer code) {}
        private record ConvertResponseData(Long timestamp, String date, String from, String to, BigDecimal amount, BigDecimal value) {}
    }
}
