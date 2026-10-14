package com.elias.mismonedas.notifications.adapter.messaging;

import com.elias.mismonedas.notifications.service.impl.NotificationDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationMessageRecovererTest {

    private final NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
    private final NotificationMessageRecoverer recoverer = new NotificationMessageRecoverer(deliveryService);

    @Test
    void shouldMarkOutboxFailedAndRejectToDeadLetterExchangeAfterRetries() {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("91");
        Message message = new Message("{}".getBytes(), properties);
        IllegalStateException failure = new IllegalStateException("smtp unavailable");

        assertThatThrownBy(() -> recoverer.recover(message, failure))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(deliveryService).markFailed(91L, "smtp unavailable");
    }
}
