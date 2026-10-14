package com.elias.mismonedas.billing.adapter.messaging;

import com.elias.mismonedas.billing.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentWebhookConsumerTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentWebhookConsumer consumer =
            new PaymentWebhookConsumer(paymentService, new ObjectMapper());

    @Test
    void shouldDelegatePersistedEventIdForProcessing() {
        Message message = new Message(
                "{\"webhookEventId\":17}".getBytes(),
                new MessageProperties()
        );

        consumer.consume(message);

        verify(paymentService).processWebhookEvent(17L);
    }
}
