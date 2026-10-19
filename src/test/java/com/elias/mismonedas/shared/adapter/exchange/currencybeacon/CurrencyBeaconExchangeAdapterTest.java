package com.elias.mismonedas.shared.adapter.exchange.currencybeacon;

import com.elias.mismonedas.shared.exception.CurrencyConversionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyBeaconExchangeAdapterTest {

    @Mock private CurrencyBeaconClient client;
    @Mock private ExchangeRateProvider rateProvider;

    private CurrencyBeaconExchangeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CurrencyBeaconExchangeAdapter(client, rateProvider);
    }

    @Test
    void shouldReturnAmountUnchangedWhenCurrenciesAreEqual() {
        BigDecimal result = adapter.convert(new BigDecimal("10.00"), "PEN", "PEN");

        assertThat(result).isEqualByComparingTo("10.00");
        verifyNoInteractions(client, rateProvider);
    }

    @Test
    void shouldConvertUsingCachedRate() {
        when(rateProvider.rates("USD")).thenReturn(Map.of("PEN", new BigDecimal("3.75")));

        BigDecimal result = adapter.convert(new BigDecimal("10.00"), "usd", "pen");

        assertThat(result).isEqualByComparingTo("37.50");
        verify(rateProvider).rates("USD");
        verifyNoInteractions(client);
    }

    @Test
    void shouldFailWhenRateIsMissing() {
        when(rateProvider.rates("USD")).thenReturn(Map.of());

        assertThatThrownBy(() -> adapter.convert(new BigDecimal("10.00"), "USD", "PEN"))
                .isInstanceOf(CurrencyConversionException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining("PEN");
    }

    @Test
    void shouldFailWhenRatesCannotBeFetched() {
        when(rateProvider.rates("USD")).thenThrow(new RuntimeException("provider down"));

        assertThatThrownBy(() -> adapter.convert(new BigDecimal("10.00"), "USD", "PEN"))
                .isInstanceOf(CurrencyConversionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldExposeCachedRates() {
        Map<String, BigDecimal> rates = Map.of("PEN", new BigDecimal("3.75"));
        when(rateProvider.rates("USD")).thenReturn(rates);

        Map<String, BigDecimal> result = adapter.getExchangeRates("usd");

        assertThat(result).isEqualTo(rates);
        verify(rateProvider).rates("USD");
    }

    @Test
    void shouldDelegateHistoricalConversionToClient() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        when(client.convert(new BigDecimal("10.00"), "USD", "PEN", date))
                .thenReturn(new BigDecimal("37.50"));

        BigDecimal result = adapter.convert(new BigDecimal("10.00"), "USD", "PEN", date);

        assertThat(result).isEqualByComparingTo("37.50");
        verifyNoInteractions(rateProvider);
    }

    @Test
    void shouldFailWhenHistoricalConversionFails() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        when(client.convert(new BigDecimal("10.00"), "USD", "PEN", date))
                .thenThrow(new RuntimeException("provider down"));

        assertThatThrownBy(() -> adapter.convert(new BigDecimal("10.00"), "USD", "PEN", date))
                .isInstanceOf(CurrencyConversionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
