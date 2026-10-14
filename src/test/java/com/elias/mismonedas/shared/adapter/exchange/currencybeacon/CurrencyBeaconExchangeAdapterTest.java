package com.elias.mismonedas.shared.adapter.exchange.currencybeacon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyBeaconExchangeAdapterTest {

    @Mock private CurrencyBeaconClient client;

    private CurrencyBeaconExchangeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CurrencyBeaconExchangeAdapter(client);
    }

    @Test
    void shouldReturnAmountUnchangedWhenCurrenciesAreEqual() {
        BigDecimal result = adapter.convert(new BigDecimal("10.00"), "PEN", "PEN");

        assertThat(result).isEqualByComparingTo("10.00");
        verify(client, never()).convert(any(), any(), any());
    }

    @Test
    void shouldDelegateToClientWhenRemoteCallSucceeds() {
        when(client.convert(new BigDecimal("10.00"), "USD", "PEN")).thenReturn(new BigDecimal("37.50"));

        BigDecimal result = adapter.convert(new BigDecimal("10.00"), "USD", "PEN");

        assertThat(result).isEqualByComparingTo("37.50");
    }

    @Test
    void shouldFallBackToCachedRatesWhenRemoteCallFails() {
        when(client.convert(new BigDecimal("10.00"), "USD", "PEN"))
                .thenThrow(new RuntimeException("circuit open"));
        when(client.getLatestRates("USD")).thenReturn(Map.of("PEN", new BigDecimal("3.75")));

        BigDecimal result = adapter.convert(new BigDecimal("10.00"), "USD", "PEN");

        assertThat(result).isEqualByComparingTo("37.50");
    }

    @Test
    void shouldReturnOriginalAmountWhenFallbackHasNoRate() {
        when(client.convert(new BigDecimal("10.00"), "USD", "PEN"))
                .thenThrow(new RuntimeException("provider down"));
        when(client.getLatestRates("USD")).thenReturn(Map.of());

        BigDecimal result = adapter.convert(new BigDecimal("10.00"), "USD", "PEN");

        assertThat(result).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldReturnEmptyRatesWhenRemoteCallFails() {
        when(client.getLatestRates("USD")).thenThrow(new RuntimeException("provider down"));

        Map<String, BigDecimal> result = adapter.getExchangeRates("USD");

        assertThat(result).isEmpty();
    }
}
