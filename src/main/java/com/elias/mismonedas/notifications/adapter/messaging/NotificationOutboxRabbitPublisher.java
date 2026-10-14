package com.elias.mismonedas.notifications.adapter.messaging;

import com.elias.mismonedas.notifications.config.NotificationRabbitConfiguration;
import com.elias.mismonedas.notifications.dto.notification.NotificationOutboxMessage;
import com.elias.mismonedas.notifications.port.NotificationOutboxPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class NotificationOutboxRabbitPublisher implements NotificationOutboxPublisher {

    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(Long outboxId) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(new NotificationOutboxMessage(outboxId));
        } catch (JsonProcessingException ex) {
            throw new AmqpException("Could not serialize notification outbox message", ex);
        }

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(outboxId.toString());
        properties.setCorrelationId(outboxId.toString());
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());

        rabbitTemplate.send(
                NotificationRabbitConfiguration.NOTIFICATION_EXCHANGE,
                NotificationRabbitConfiguration.NOTIFICATION_ROUTING_KEY,
                new Message(body, properties),
                correlationData
        );

        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (correlationData.getReturned() != null) {
                throw new AmqpException("Notification outbox message was returned as unroutable: "
                        + correlationData.getReturned());
            }
            if (!confirm.ack()) {
                throw new AmqpException("Notification outbox publish was nacked: " + confirm.reason());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted waiting for notification publisher confirm", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new AmqpException("No publisher confirm for notification outbox id=" + outboxId, ex);
        }
    }
}
