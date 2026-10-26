package com.elias.mismonedas.document.adapter.ocr;

import com.elias.mismonedas.document.dto.ReceiptData;
import com.elias.mismonedas.shared.adapter.ai.gemini.InteractionClient;
import com.elias.mismonedas.shared.adapter.ai.gemini.dto.InteractionRequest;
import com.elias.mismonedas.shared.adapter.ai.gemini.dto.InteractionResponse;
import com.elias.mismonedas.shared.exception.OcrProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiReceiptParserAdapterTest {

    private final InteractionClient client = mock(InteractionClient.class);
    private final GeminiReceiptParserAdapter adapter =
            new GeminiReceiptParserAdapter(client, new ObjectMapper());

    {
        ReflectionTestUtils.setField(adapter, "model", "gemini-test");
    }

    @Test
    void shouldParseValidResponse() {
        when(client.create(any(InteractionRequest.class))).thenReturn(response("""
                {"amount":"45.50","date":"2026-07-01","description":"Tienda",
                 "currency":"PEN","paymentMethod":"CASH","transactionType":"SPENT"}
                """));

        ReceiptData data = adapter.parse("TOTAL S/ 45.50");

        assertThat(data.amount()).isEqualByComparingTo(new BigDecimal("45.50"));
        assertThat(data.date()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(data.description()).isEqualTo("Tienda");
        assertThat(data.currencyGuess()).isEqualTo("PEN");
    }

    @Test
    void shouldIgnoreInvalidAmountAndDateWithoutThrowing() {
        when(client.create(any(InteractionRequest.class))).thenReturn(response("""
                {"amount":"no-es-numero","date":"31/02/2025","description":"Tienda"}
                """));

        ReceiptData data = adapter.parse("texto");

        assertThat(data.amount()).isNull();
        assertThat(data.date()).isNull();
        assertThat(data.description()).isEqualTo("Tienda");
    }

    @Test
    void shouldThrowOcrProcessingExceptionWhenJsonIsInvalid() {
        when(client.create(any(InteractionRequest.class))).thenReturn(response("no-es-json"));

        assertThatThrownBy(() -> adapter.parse("texto"))
                .isInstanceOf(OcrProcessingException.class);
    }

    @Test
    void shouldThrowOcrProcessingExceptionWhenResponseHasNoText() {
        when(client.create(any(InteractionRequest.class))).thenReturn(response(null));

        assertThatThrownBy(() -> adapter.parse("texto"))
                .isInstanceOf(OcrProcessingException.class);
    }

    private InteractionResponse response(String text) {
        if (text == null) {
            return new InteractionResponse("id", "completed", "gemini-test", List.of(), null);
        }
        InteractionResponse.Content content = new InteractionResponse.Content("text", text);
        InteractionResponse.Step step = new InteractionResponse.Step.ModelOutputStep(List.of(content));
        return new InteractionResponse("id", "completed", "gemini-test", List.of(step), null);
    }
}
