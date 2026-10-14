package com.elias.mismonedas.notifications.adapter.messaging;

import com.elias.mismonedas.notifications.service.impl.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationMessageRecoverer implements MessageRecoverer {

    private final NotificationDeliveryService deliveryService;

    @Override
    public void recover(Message message, Throwable cause) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId != null) {
            log.error("Notification outbox retries exhausted; routing id={} to DLQ", messageId, cause);
            try {
                deliveryService.markFailed(Long.valueOf(messageId), cause.getMessage());
            } catch (RuntimeException ex) {
                log.error("Could not mark notification outbox as FAILED id={}", messageId, ex);
            }
        } else {
            log.error("Notification retries exhausted for message without messageId; routing to DLQ", cause);
        }

        throw new AmqpRejectAndDontRequeueException("Notification retries exhausted", cause);
    }
}
