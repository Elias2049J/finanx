package com.elias.mismonedas.document.adapter.messaging;

import com.elias.mismonedas.document.config.ReceiptScanRabbitConfiguration;
import com.elias.mismonedas.document.dto.scan.ReceiptScanJobMessage;
import com.elias.mismonedas.document.outbox.ReceiptScanOutbox;
import com.elias.mismonedas.document.outbox.persistence.ReceiptScanOutboxRepository;
import com.elias.mismonedas.document.port.ReceiptScanJobPublisher;
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
public class ReceiptScanRabbitPublisher implements ReceiptScanJobPublisher {

    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ReceiptScanOutboxRepository outboxRepository;

    @Override
    public void publish(Long outboxId) {
        ReceiptScanOutbox outbox = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new AmqpException("Receipt scan outbox not found: " + outboxId));

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(new ReceiptScanJobMessage(
                    outbox.getReceiptId(), outbox.getBucket(), outbox.getObjectKey()));
        } catch (JsonProcessingException ex) {
            throw new AmqpException("Could not serialize receipt scan job message", ex);
        }

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(outbox.getReceiptId().toString());
        properties.setCorrelationId(outboxId.toString());

        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.send(
                ReceiptScanRabbitConfiguration.SCAN_EXCHANGE,
                ReceiptScanRabbitConfiguration.SCAN_ROUTING_KEY,
                new Message(body, properties),
                correlationData
        );

        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (correlationData.getReturned() != null) {
                throw new AmqpException("Receipt scan job was returned as unroutable: " + correlationData.getReturned());
            }
            if (!confirm.ack()) {
                throw new AmqpException("Receipt scan job publish was nacked: " + confirm.reason());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted waiting for receipt scan publisher confirm", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new AmqpException("No publisher confirm for receipt scan outbox id=" + outboxId, ex);
        }
    }
}
