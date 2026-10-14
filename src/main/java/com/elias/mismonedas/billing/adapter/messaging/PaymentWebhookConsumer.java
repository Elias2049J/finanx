package com.elias.mismonedas.billing.adapter.messaging;

import com.elias.mismonedas.billing.config.PaymentWebhookRabbitConfiguration;
import com.elias.mismonedas.billing.dto.webhook.PaymentWebhookMessage;
import com.elias.mismonedas.billing.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @RabbitListener(
            queues = PaymentWebhookRabbitConfiguration.PAYMENT_WEBHOOK_QUEUE,
            containerFactory = PaymentWebhookRabbitConfiguration.PAYMENT_WEBHOOK_LISTENER_FACTORY
    )
    public void consume(Message message) {
        PaymentWebhookMessage webhookMessage;
        try {
            webhookMessage = objectMapper.readValue(message.getBody(), PaymentWebhookMessage.class);
        } catch (IOException ex) {
            throw new MessageConversionException("Invalid payment webhook message", ex);
        }

        if (webhookMessage.webhookEventId() == null) {
            throw new MessageConversionException("Payment webhook message has no webhookEventId");
        }

        log.debug("Consuming payment webhook event id={}", webhookMessage.webhookEventId());
        paymentService.processWebhookEvent(webhookMessage.webhookEventId());
    }
}
