package com.elias.mismonedas.document.adapter.messaging;

import com.elias.mismonedas.document.config.ReceiptScanRabbitConfiguration;
import com.elias.mismonedas.document.outbox.ReceiptScanOutbox;
import com.elias.mismonedas.document.outbox.ReceiptScanOutboxState;
import com.elias.mismonedas.document.outbox.persistence.ReceiptScanOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptScanRabbitPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ReceiptScanOutboxRepository outboxRepository;

    private ReceiptScanRabbitPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ReceiptScanRabbitPublisher(rabbitTemplate, new ObjectMapper(), outboxRepository);
    }

    @Test
    void shouldPublishJobAsPersistentMessageAndWaitForConfirm() {
        ReceiptScanOutbox outbox = ReceiptScanOutbox.builder()
                .id(74L).receiptId(5L).bucket("finanx-images").objectKey("images/1/receipt.jpg")
                .state(ReceiptScanOutboxState.PENDING)
                .build();
        when(outboxRepository.findById(74L)).thenReturn(Optional.of(outbox));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(
                eq(ReceiptScanRabbitConfiguration.SCAN_EXCHANGE),
                eq(ReceiptScanRabbitConfiguration.SCAN_ROUTING_KEY),
                any(Message.class),
                any(CorrelationData.class));

        publisher.publish(74L);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(ReceiptScanRabbitConfiguration.SCAN_EXCHANGE),
                eq(ReceiptScanRabbitConfiguration.SCAN_ROUTING_KEY),
                messageCaptor.capture(),
                any(CorrelationData.class));
        Message message = messageCaptor.getValue();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"receiptId\":5,\"bucket\":\"finanx-images\",\"objectKey\":\"images/1/receipt.jpg\"}");
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo("5");
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test
    void shouldFailWhenBrokerNacksPublish() {
        ReceiptScanOutbox outbox = ReceiptScanOutbox.builder()
                .id(75L).receiptId(6L).bucket("b").objectKey("k")
                .state(ReceiptScanOutboxState.PENDING)
                .build();
        when(outboxRepository.findById(75L)).thenReturn(Optional.of(outbox));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "no route"));
            return null;
        }).when(rabbitTemplate).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(75L))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("nacked");
    }
}
