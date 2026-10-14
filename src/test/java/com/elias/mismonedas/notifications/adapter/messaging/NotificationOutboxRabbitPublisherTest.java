package com.elias.mismonedas.notifications.adapter.messaging;

import com.elias.mismonedas.notifications.config.NotificationRabbitConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxRabbitPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    private NotificationOutboxRabbitPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new NotificationOutboxRabbitPublisher(rabbitTemplate, new ObjectMapper());
    }

    @Test
    void shouldPublishOnlyOutboxIdAsPersistentMessageAndWaitForConfirm() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(
                eq(NotificationRabbitConfiguration.NOTIFICATION_EXCHANGE),
                eq(NotificationRabbitConfiguration.NOTIFICATION_ROUTING_KEY),
                any(Message.class),
                any(CorrelationData.class)
        );

        publisher.publish(74L);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(NotificationRabbitConfiguration.NOTIFICATION_EXCHANGE),
                eq(NotificationRabbitConfiguration.NOTIFICATION_ROUTING_KEY),
                messageCaptor.capture(),
                any(CorrelationData.class)
        );
        Message message = messageCaptor.getValue();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"outboxId\":74}");
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo("74");
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test
    void shouldFailRelayWhenBrokerNacksPublish() {
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
