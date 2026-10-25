package com.elias.mismonedas.notifications.adapter.messaging;

import com.elias.mismonedas.notifications.service.impl.NotificationDeliveryService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeadLetterConsumerTest {

    private final NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
    private final Channel channel = mock(Channel.class);
    private final NotificationDeadLetterConsumer consumer = new NotificationDeadLetterConsumer(deliveryService);

    @Test
    void shouldScheduleOutboxRetryAndAcknowledgeTheDeadLetter() throws Exception {
        Message message = message("41", 81L);
        when(deliveryService.scheduleAutomaticRetry(41L, "Rabbit retries exhausted")).thenReturn(true);

        consumer.consume(message, channel);

        verify(deliveryService).scheduleAutomaticRetry(41L, "Rabbit retries exhausted");
        verify(channel).basicAck(81L, false);
    }

    @Test
    void shouldRequeueDeadLetterWhenRetryCannotBeRecorded() throws Exception {
        Message message = message("42", 82L);
        doThrow(new IllegalStateException("database unavailable"))
                .when(deliveryService).scheduleAutomaticRetry(42L, "Rabbit retries exhausted");

        consumer.consume(message, channel);

        verify(channel).basicNack(82L, false, true);
    }

    @Test
    void shouldAcknowledgeDeadLetterWithoutAnOutboxId() throws Exception {
        consumer.consume(message(null, 83L), channel);

        verify(channel).basicAck(83L, false);
    }

    private Message message(String messageId, long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(messageId);
        properties.setDeliveryTag(deliveryTag);
        return new Message("{}".getBytes(), properties);
    }
}
