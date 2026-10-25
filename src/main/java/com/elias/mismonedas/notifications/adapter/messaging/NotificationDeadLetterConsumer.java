package com.elias.mismonedas.notifications.adapter.messaging;

import com.elias.mismonedas.notifications.config.NotificationRabbitConfiguration;
import com.elias.mismonedas.notifications.service.impl.NotificationDeliveryService;
import com.rabbitmq.client.Channel;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDeadLetterConsumer {

    private final NotificationDeliveryService deliveryService;

    @RabbitListener(
            queues = NotificationRabbitConfiguration.NOTIFICATION_DLQ,
            containerFactory = NotificationRabbitConfiguration.NOTIFICATION_DLQ_LISTENER_FACTORY
    )
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        Long outboxId = parseOutboxId(message.getMessageProperties().getMessageId());
        if (outboxId == null) {
            log.error("Dead-letter notification has no numeric outbox id; acknowledging and discarding");
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            boolean scheduled = deliveryService.scheduleAutomaticRetry(outboxId, "Rabbit retries exhausted");
            log.info("Dead-letter notification reconciled outbox_id={} retry_scheduled={}", outboxId, scheduled);
            channel.basicAck(deliveryTag, false);
        } catch (EntityNotFoundException ex) {
            log.error("Dead-letter notification references missing outbox_id={}; acknowledging", outboxId, ex);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException ex) {
            log.error("Could not persist dead-letter retry for outbox_id={}; requeueing", outboxId, ex);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private Long parseOutboxId(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(messageId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
