package com.elias.mismonedas.document.adapter.messaging;

import com.elias.mismonedas.document.service.ReceiptScanService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReceiptScanMessageRecovererTest {

    private final ReceiptScanService receiptScanService = mock(ReceiptScanService.class);
    private final ReceiptScanMessageRecoverer recoverer = new ReceiptScanMessageRecoverer(receiptScanService);

    @Test
    void shouldMarkReceiptFailedAndRejectMessage() {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("42");
        Message message = new Message("{}".getBytes(), properties);

        assertThatThrownBy(() -> recoverer.recover(message, new RuntimeException("boom")))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);

        verify(receiptScanService).markFailed(eq(42L), anyString());
    }
}
