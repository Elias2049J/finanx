package com.elias.mismonedas.shared.money;

import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.port.ExchangePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyConverterTest {

    @Mock private ExchangePort exchangePort;

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter(exchangePort);
    }

    @Test
    void shouldNotConvertWhenCurrencyIsTheSame() {
        Money result = converter.convert(Money.of(new BigDecimal("10.123"), "PEN"), "PEN");

        assertThat(result.amount()).isEqualByComparingTo("10.123");
        assertThat(result.currencyCode()).isEqualTo("PEN");
    }

    @Test
    void shouldConvertAndRoundToTwoDecimals() {
        when(exchangePort.convert(new BigDecimal("10.00"), "USD", "PEN")).thenReturn(new BigDecimal("37.456"));

        Money result = converter.convert(Money.of(new BigDecimal("10.00"), "USD"), "PEN");

        assertThat(result.amount()).isEqualByComparingTo("37.46");
        assertThat(result.amount().scale()).isEqualTo(2);
        assertThat(result.currencyCode()).isEqualTo("PEN");
    }

    @Test
    void shouldPropagateConversionFailure() {
        when(exchangePort.convert(any(), eq("USD"), eq("PEN")))
                .thenThrow(new RuntimeException("provider down"));

        assertThatThrownBy(() -> converter.convert(Money.of(new BigDecimal("10.00"), "USD"), "PEN"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("provider down");
    }

    @Test
    void shouldRelabelWhenSourceCurrencyIsNull() {
        Money result = converter.convert(Money.of(new BigDecimal("10.00"), null), "PEN");

        assertThat(result.amount()).isEqualByComparingTo("10.00");
        assertThat(result.currencyCode()).isEqualTo("PEN");
    }

    @Test
    void shouldNotRelabelWhenTargetCurrencyIsNull() {
        Money result = converter.convert(Money.of(new BigDecimal("10.00"), "USD"), null);

        assertThat(result.currencyCode()).isEqualTo("USD");
        assertThat(result.amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(converter.convert(null, "PEN")).isNull();
    }
}
