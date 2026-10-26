package com.elias.mismonedas.document.adapter.ocr;

import com.elias.mismonedas.document.dto.ReceiptData;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
    void shouldReturnNullDateAndDescription() {
        ReceiptData result = adapter.parse("TOTAL S/. 99.99");
        assertThat(result.date()).isNull();
        assertThat(result.description()).isNull();
    }

    @Test
    void shouldDetectCashPaymentMethod() {
        ReceiptData result = adapter.parse("EFECTIVO\nTOTAL S/. 50.00");
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.CASH);
    }

    @Test
    void shouldDetectCreditCardPaymentMethod() {
        ReceiptData result = adapter.parse("TARJETA CREDITO VISA\nTOTAL S/. 100.00");
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
    }

    @Test
    void shouldDetectYapePaymentMethod() {
        ReceiptData result = adapter.parse("YAPE\nTOTAL S/. 25.00");
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.YAPE);
    }

    @Test
    void shouldDetectPlinPaymentMethod() {
        ReceiptData result = adapter.parse("PLIN\nTOTAL S/. 30.00");
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.PLIN);
    }

    @Test
    void shouldDetectSpentTransactionType() {
        ReceiptData result = adapter.parse("BOLETA DE VENTA\nTOTAL S/. 60.00");
        assertThat(result.transactionType()).isEqualTo(TransactionType.SPENT);
    }

    @Test
    void shouldDetectIncomeTransactionType() {
        ReceiptData result = adapter.parse("COBRO\nTOTAL S/. 200.00");
        assertThat(result.transactionType()).isEqualTo(TransactionType.INCOME);
    }

    @Test
    void shouldReturnNullPaymentMethodWhenNotDetected() {
        ReceiptData result = adapter.parse("TOTAL S/. 45.00");
        assertThat(result.paymentMethod()).isNull();
    }

    @Test
    void shouldReturnNullTransactionTypeWhenNotDetected() {
        ReceiptData result = adapter.parse("TOTAL S/. 45.00");
        assertThat(result.transactionType()).isNull();
    }

    @Test
    void shouldExtractDateFromReceipt() {
        ReceiptData result = adapter.parse("FECHA DE EMISION: 01-07-2026\nTOTAL S/. 45.00");
        assertThat(result.date()).isEqualTo(java.time.LocalDate.of(2026, 7, 1));
    }

    @Test
    void shouldReturnNullDateWhenNotPresent() {
        ReceiptData result = adapter.parse("TOTAL S/. 45.00");
        assertThat(result.date()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "TOTAL S/ 12.50|PEN",
            "TOTAL S/.12.50|PEN",
            "TOTAL SOLES 12.50|PEN",
            "TOTAL USD 10.00|USD",
            "TOTAL US$ 10.00|USD",
            "TOTAL DOLARES 10.00|USD",
            "TOTAL € 5,00|EUR",
            "TOTAL EUROS 5,00|EUR",
            "TOTAL 12.50|PEN",
            "TOTAL $ 10.00|PEN"
    })
    void shouldDetectCurrencyWithoutFalsePositives(String text, String expectedCurrency) {
        ReceiptData result = adapter.parse(text);

        assertThat(result.currencyGuess()).isEqualTo(expectedCurrency);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "TOTAL 12.50|12.50",
            "TOTAL S/ 12.50|12.50",
            "TOTAL S/.12.50|12.50",
            "TOTAL 12,50|12.50",
            "TOTAL 1.234,56|1234.56",
            "TOTAL 1,234.56|1234.56",
            "TOTAL 1.234|1234",
            "TOTAL 1234.56|1234.56"
    })
    void shouldParseAmountsWithDifferentSeparators(String text, String expectedAmount) {
        ReceiptData result = adapter.parse(text);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal(expectedAmount));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "FECHA DE EMISION: 01-07-2026|2026-07-01",
            "FECHA DE EMISION: 1/7/2026|2026-07-01",
            "FECHA DE EMISION: 01.07.2026|2026-07-01"
    })
    void shouldParseValidDatesStrictly(String text, String expectedDate) {
        ReceiptData result = adapter.parse(text + "\nTOTAL S/ 45.00");

        assertThat(result.date()).isEqualTo(LocalDate.parse(expectedDate));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "FECHA DE EMISION: 31/02/2025",
            "FECHA DE EMISION: 01/07/2999",
            "FECHA DE EMISION: 01/07/1999"
    })
    void shouldIgnoreInvalidOrImplausibleDates(String text) {
        ReceiptData result = adapter.parse(text + "\nTOTAL S/ 45.00");

        assertThat(result.date()).isNull();
    }

    @Test
    void shouldUseFirstValidDateWhenAnEarlierDateIsInvalid() {
        ReceiptData result = adapter.parse(
                "FECHA DE EMISION: 31/02/2025\nFECHA DE EMISION: 01/07/2026\nTOTAL S/ 45.00");

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void shouldNotThrowForNullOrMalformedText() {
        assertThatCode(() -> adapter.parse(null)).doesNotThrowAnyException();
        assertThatCode(() -> adapter.parse("TOTAL 1.2.3,abc\nFECHA DE EMISION: 31/02/2025"))
                .doesNotThrowAnyException();
    }
}
