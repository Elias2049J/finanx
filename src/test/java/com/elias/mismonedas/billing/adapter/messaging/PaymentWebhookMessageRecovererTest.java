package com.elias.mismonedas.billing.adapter.messaging;

import com.elias.mismonedas.billing.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentWebhookMessageRecovererTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentWebhookMessageRecoverer recoverer = new PaymentWebhookMessageRecoverer(paymentService);

    @Test
    void shouldPersistFailedStateAndRejectToDeadLetterExchangeAfterExhaustion() {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("29");
        Message message = new Message("{}".getBytes(), properties);
        IllegalStateException failure = new IllegalStateException("temporary failure");

        assertThatThrownBy(() -> recoverer.recover(message, failure))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(paymentService).markWebhookEventFailed(29L, "temporary failure");
    }
}
