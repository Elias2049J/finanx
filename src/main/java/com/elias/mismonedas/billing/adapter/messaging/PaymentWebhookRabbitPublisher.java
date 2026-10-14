package com.elias.mismonedas.billing.adapter.messaging;

import com.elias.mismonedas.billing.dto.webhook.PaymentWebhookMessage;
import com.elias.mismonedas.billing.port.PaymentWebhookPublisher;
import com.elias.mismonedas.billing.config.PaymentWebhookRabbitConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentWebhookRabbitPublisher implements PaymentWebhookPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(Long webhookEventId) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(new PaymentWebhookMessage(webhookEventId));
        } catch (JsonProcessingException ex) {
            throw new AmqpException("Could not serialize payment webhook message", ex);
        }

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(webhookEventId.toString());
        properties.setCorrelationId(webhookEventId.toString());

        rabbitTemplate.send(
                PaymentWebhookRabbitConfiguration.PAYMENT_WEBHOOK_EXCHANGE,
                PaymentWebhookRabbitConfiguration.PAYMENT_WEBHOOK_ROUTING_KEY,
                new Message(body, properties)
        );
    }
}
