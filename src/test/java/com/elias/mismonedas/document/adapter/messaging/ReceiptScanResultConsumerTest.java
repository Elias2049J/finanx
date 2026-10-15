package com.elias.mismonedas.document.adapter.messaging;

import com.elias.mismonedas.document.dto.scan.ReceiptScanResultMessage;
import com.elias.mismonedas.document.service.ReceiptScanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReceiptScanResultConsumerTest {

    private final ReceiptScanService receiptScanService = mock(ReceiptScanService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReceiptScanResultConsumer consumer = new ReceiptScanResultConsumer(receiptScanService, objectMapper);

    @Test
    void shouldApplyParsedResult() throws Exception {
        Map<String, Object> payload = Map.of(
                "receiptId", 12L,
                "status", "DONE",
                "rawText", "TOTAL S/. 45.00",
                "engine", "rapidocr-ppocrv6",
                "engineConfidence", 0.93,
                "durationMs", 1500L);
        Message message = new Message(objectMapper.writeValueAsBytes(payload), new MessageProperties());

        consumer.consume(message);

        ArgumentCaptor<ReceiptScanResultMessage> captor = ArgumentCaptor.forClass(ReceiptScanResultMessage.class);
        verify(receiptScanService).applyScanResult(captor.capture());
        assertThat(captor.getValue().receiptId()).isEqualTo(12L);
        assertThat(captor.getValue().status()).isEqualTo("DONE");
        assertThat(captor.getValue().rawText()).isEqualTo("TOTAL S/. 45.00");
    }

    @Test
    void shouldRejectMessageWithoutReceiptId() throws Exception {
        Message message = new Message(
                objectMapper.writeValueAsBytes(Map.of("status", "DONE")), new MessageProperties());

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(MessageConversionException.class)
                .hasMessageContaining("receiptId");
    }

    @Test
    void shouldRejectInvalidJson() {
        Message message = new Message("not-json".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        assertThatThrownBy(() -> consumer.consume(message))
                .isInstanceOf(MessageConversionException.class);
    }
}
