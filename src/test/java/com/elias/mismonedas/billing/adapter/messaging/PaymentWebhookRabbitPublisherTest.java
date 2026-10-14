package com.elias.mismonedas.billing.adapter.messaging;

import com.elias.mismonedas.billing.config.PaymentWebhookRabbitConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentWebhookRabbitPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final PaymentWebhookRabbitPublisher publisher =
            new PaymentWebhookRabbitPublisher(rabbitTemplate, new ObjectMapper());

    @Test
    void shouldPublishPersistentMessageContainingOnlyTheStoredEventId() {
        publisher.publish(42L);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(PaymentWebhookRabbitConfiguration.PAYMENT_WEBHOOK_EXCHANGE),
                eq(PaymentWebhookRabbitConfiguration.PAYMENT_WEBHOOK_ROUTING_KEY),
                messageCaptor.capture()
        );

        Message message = messageCaptor.getValue();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"webhookEventId\":42}");
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo("42");
    }

    @Test
    void shouldPropagateBrokerFailureToTheDurableIntakeBoundary() {
        doThrow(new AmqpException("broker unavailable"))
                .when(rabbitTemplate)
                .send(eq(PaymentWebhookRabbitConfiguration.PAYMENT_WEBHOOK_EXCHANGE),
                        eq(PaymentWebhookRabbitConfiguration.PAYMENT_WEBHOOK_ROUTING_KEY), any(Message.class));

        assertThatThrownBy(() -> publisher.publish(42L))
                .isInstanceOf(AmqpException.class)
                .hasMessage("broker unavailable");
    }
}
