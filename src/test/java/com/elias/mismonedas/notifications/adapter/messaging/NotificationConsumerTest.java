package com.elias.mismonedas.notifications.adapter.messaging;

import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.notifications.outbox.NotificationDeliveryChannel;
import com.elias.mismonedas.notifications.port.NotificationPort;
import com.elias.mismonedas.notifications.service.impl.NotificationDeliveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationConsumerTest {

    private final NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
    private final NotificationPort fcmPort = mock(NotificationPort.class);
    private final NotificationPort emailPort = mock(NotificationPort.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDispatchEachChannelForThePersistedOutboxId() throws Exception {
        NotificationDTO dto = NotificationDTO.builder()
                .id(10L)
                .type(NotificationType.INFO)
                .message("hello")
                .build();
        when(fcmPort.channel()).thenReturn(NotificationDeliveryChannel.FCM);
        when(emailPort.channel()).thenReturn(NotificationDeliveryChannel.EMAIL);
        when(deliveryService.prepare(74L, List.of(fcmPort, emailPort))).thenReturn(Optional.of(dto));
        NotificationConsumer consumer = new NotificationConsumer(
                deliveryService, List.of(fcmPort, emailPort), objectMapper);
        Message message = new Message(
                objectMapper.writeValueAsBytes(Map.of("outboxId", 74L)),
                new MessageProperties()
        );

        consumer.consume(message);

        verify(deliveryService).deliverChannel(74L, fcmPort, dto);
        verify(deliveryService).deliverChannel(74L, emailPort, dto);
        verify(deliveryService).complete(74L);
    }

    @Test
    void shouldAcknowledgeTerminalOutboxWithoutCallingDeliveryPorts() throws Exception {
        when(deliveryService.prepare(75L, List.of(fcmPort, emailPort))).thenReturn(Optional.empty());
        NotificationConsumer consumer = new NotificationConsumer(
                deliveryService, List.of(fcmPort, emailPort), objectMapper);
        Message message = new Message(
                objectMapper.writeValueAsBytes(Map.of("outboxId", 75L)),
                new MessageProperties()
        );

        consumer.consume(message);

        verify(deliveryService, org.mockito.Mockito.never())
                .deliverChannel(ArgumentMatchers.anyLong(), ArgumentMatchers.any(), ArgumentMatchers.any());
        verify(deliveryService, org.mockito.Mockito.never()).complete(75L);
    }
}
