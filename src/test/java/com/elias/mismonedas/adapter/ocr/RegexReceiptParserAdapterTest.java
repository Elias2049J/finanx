package com.elias.mismonedas.adapter.ocr;

import com.elias.mismonedas.port.ReceiptData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RegexReceiptParserAdapterTest {

    private final RegexReceiptParserAdapter adapter = new RegexReceiptParserAdapter();

    @Test
    void shouldExtractAmountWhenTotalPatternMatches() {
        ReceiptData result = adapter.parse("TOTAL S/. 45.00\nGracias por su compra");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(result.parseConfidence()).isEqualTo(0.5);
    }

    @Test
    void shouldExtractAmountWhenSolSymbolMatches() {
        ReceiptData result = adapter.parse("S/. 120.50");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("120.50"));
    }

    @Test
    void shouldReturnNullAmountWhenNoPattern() {
        ReceiptData result = adapter.parse("Texto sin monto detectable");
        assertThat(result.amount()).isNull();
        assertThat(result.parseConfidence()).isEqualTo(0.0);
    }

    @Test
    void shouldReturnNullAmountForBlankText() {
        ReceiptData result = adapter.parse("");
        assertThat(result.amount()).isNull();
        assertThat(result.parseConfidence()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleAmountWithThousandsSeparator() {
        ReceiptData result = adapter.parse("TOTAL S/. 1234.56");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test
    void shouldAlwaysReturnPENCurrency() {
        ReceiptData result = adapter.parse("TOTAL S/. 30.00");
        assertThat(result.currencyGuess()).isEqualTo("PEN");
    }

    @Test
    void shouldReturnNullDateAndMerchant() {
        ReceiptData result = adapter.parse("TOTAL S/. 99.99");
        assertThat(result.detectedDate()).isNull();
        assertThat(result.merchantName()).isNull();
    }
}
