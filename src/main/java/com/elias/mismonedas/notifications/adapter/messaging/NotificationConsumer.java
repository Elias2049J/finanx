package com.elias.mismonedas.notifications.adapter.messaging;

import com.elias.mismonedas.notifications.config.NotificationRabbitConfiguration;
import com.elias.mismonedas.notifications.dto.notification.NotificationOutboxMessage;
import com.elias.mismonedas.notifications.port.NotificationPort;
import com.elias.mismonedas.notifications.service.impl.NotificationDeliveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationDeliveryService deliveryService;
    private final List<NotificationPort> notificationPorts;
    private final ObjectMapper objectMapper;

    @RabbitListener(
            queues = NotificationRabbitConfiguration.NOTIFICATION_QUEUE,
            containerFactory = NotificationRabbitConfiguration.NOTIFICATION_LISTENER_FACTORY
    )
    public void consume(Message message) {
        NotificationOutboxMessage envelope;
        try {
            envelope = objectMapper.readValue(message.getBody(), NotificationOutboxMessage.class);
        } catch (IOException ex) {
            throw new MessageConversionException("Invalid notification outbox message", ex);
        }
        if (envelope.outboxId() == null) {
            throw new MessageConversionException("Notification outbox message has no outboxId");
        }

        deliveryService.prepare(envelope.outboxId(), notificationPorts)
                .ifPresent(dto -> {
                    notificationPorts.forEach(port ->
                            deliveryService.deliverChannel(envelope.outboxId(), port, dto));
                    deliveryService.complete(envelope.outboxId());
                    log.debug("Notification outbox processed id={}", envelope.outboxId());
                });
    }
}
