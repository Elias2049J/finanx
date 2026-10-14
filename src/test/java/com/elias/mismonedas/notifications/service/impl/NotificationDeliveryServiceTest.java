package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.notifications.outbox.NotificationDelivery;
import com.elias.mismonedas.notifications.outbox.NotificationDeliveryChannel;
import com.elias.mismonedas.notifications.outbox.NotificationDeliveryState;
import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationDeliveryRepository;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.port.NotificationPort;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private NotificationDeliveryRepository deliveryRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPort fcmPort;
    @Mock private NotificationPort emailPort;

    private NotificationDeliveryService service;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        service = new NotificationDeliveryService(
                outboxRepository, deliveryRepository, notificationRepository, objectMapper);
    }

    @Test
    void shouldCreateOneDeliveryRecordPerChannelFromThePersistedOutbox() throws Exception {
        when(fcmPort.channel()).thenReturn(NotificationDeliveryChannel.FCM);
        when(emailPort.channel()).thenReturn(NotificationDeliveryChannel.EMAIL);
        NotificationOutbox outbox = outbox(10L, OutboxState.PUBLISHED);
        when(outboxRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(outbox));
        when(notificationRepository.findById(20L)).thenReturn(Optional.of(notification(20L, NotificationState.SCHEDULED)));
        when(deliveryRepository.findAllByOutboxIdOrderByChannelAsc(10L)).thenReturn(List.of());

        Optional<NotificationDTO> prepared = service.prepare(10L, List.of(fcmPort, emailPort));

        assertThat(prepared).isPresent();
        assertThat(prepared.get().getId()).isEqualTo(20L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationDelivery>> deliveriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(deliveriesCaptor.capture());
        assertThat(deliveriesCaptor.getValue())
                .extracting(NotificationDelivery::getChannel)
                .containsExactlyInAnyOrder(NotificationDeliveryChannel.FCM, NotificationDeliveryChannel.EMAIL);
        assertThat(deliveriesCaptor.getValue())
                .extracting(NotificationDelivery::getState)
                .containsOnly(NotificationDeliveryState.PENDING);
    }

    @Test
    void shouldSkipOutboxWhenNotificationWasDiscardedBeforeDelivery() throws Exception {
        when(fcmPort.channel()).thenReturn(NotificationDeliveryChannel.FCM);
        when(emailPort.channel()).thenReturn(NotificationDeliveryChannel.EMAIL);
        NotificationOutbox outbox = outbox(11L, OutboxState.PUBLISHED);
        Notification notification = notification(21L, NotificationState.DISCARD);
        NotificationDelivery fcmDelivery = delivery(11L, NotificationDeliveryChannel.FCM);
        NotificationDelivery emailDelivery = delivery(11L, NotificationDeliveryChannel.EMAIL);
        when(outboxRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(outbox));
        when(notificationRepository.findById(21L)).thenReturn(Optional.of(notification));
        when(deliveryRepository.findAllByOutboxIdOrderByChannelAsc(11L))
                .thenReturn(List.of(fcmDelivery, emailDelivery));

        Optional<NotificationDTO> prepared = service.prepare(11L, List.of(fcmPort, emailPort));

        assertThat(prepared).isEmpty();
        assertThat(outbox.getState()).isEqualTo(OutboxState.SKIPPED);
        assertThat(fcmDelivery.getState()).isEqualTo(NotificationDeliveryState.SKIPPED);
        assertThat(emailDelivery.getState()).isEqualTo(NotificationDeliveryState.SKIPPED);
    }

    @Test
    void shouldRecordSuccessfulChannelSoLaterRetrySkipsIt() throws Exception {
        when(fcmPort.channel()).thenReturn(NotificationDeliveryChannel.FCM);
        NotificationOutbox outbox = outbox(12L, OutboxState.PUBLISHED);
        NotificationDTO dto = objectMapper.readValue(outbox.getPayload(), NotificationDTO.class);
        NotificationDelivery fcmDelivery = delivery(12L, NotificationDeliveryChannel.FCM);
        when(outboxRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(outbox));
        when(notificationRepository.findById(22L)).thenReturn(Optional.of(notification(22L, NotificationState.SCHEDULED)));
        when(deliveryRepository.findByOutboxIdAndChannelForUpdate(12L, NotificationDeliveryChannel.FCM))
                .thenReturn(Optional.of(fcmDelivery));

        service.deliverChannel(12L, fcmPort, dto);

        assertThat(fcmDelivery.getState()).isEqualTo(NotificationDeliveryState.SENT);
        assertThat(fcmDelivery.getSentAt()).isNotNull();

        service.deliverChannel(12L, fcmPort, dto);

        verify(fcmPort).send(dto);
    }

    @Test
    void shouldLeaveChannelPendingWhenDeliveryThrowsSoBrokerRetryCanRetryIt() throws Exception {
        when(emailPort.channel()).thenReturn(NotificationDeliveryChannel.EMAIL);
        NotificationOutbox outbox = outbox(13L, OutboxState.PUBLISHED);
        NotificationDTO dto = objectMapper.readValue(outbox.getPayload(), NotificationDTO.class);
        NotificationDelivery emailDelivery = delivery(13L, NotificationDeliveryChannel.EMAIL);
        when(outboxRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(outbox));
        when(notificationRepository.findById(23L)).thenReturn(Optional.of(notification(23L, NotificationState.SCHEDULED)));
        when(deliveryRepository.findByOutboxIdAndChannelForUpdate(13L, NotificationDeliveryChannel.EMAIL))
                .thenReturn(Optional.of(emailDelivery));
        doThrow(new IllegalStateException("SMTP unavailable")).when(emailPort).send(dto);

        assertThatThrownBy(() -> service.deliverChannel(13L, emailPort, dto))
                .isInstanceOf(IllegalStateException.class);

        assertThat(emailDelivery.getState()).isEqualTo(NotificationDeliveryState.PENDING);
    }

    @Test
    void shouldMarkOutboxFailedAndPendingDeliveriesFailedAfterRetryExhaustion() throws Exception {
        NotificationOutbox outbox = outbox(14L, OutboxState.PUBLISHED);
        NotificationDelivery pending = delivery(14L, NotificationDeliveryChannel.EMAIL);
        when(outboxRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(outbox));
        when(deliveryRepository.findAllByOutboxIdOrderByChannelAsc(14L)).thenReturn(List.of(pending));

        service.markFailed(14L, "SMTP unavailable");

        assertThat(outbox.getState()).isEqualTo(OutboxState.FAILED);
        assertThat(pending.getState()).isEqualTo(NotificationDeliveryState.FAILED);
        assertThat(pending.getErrorMessage()).isEqualTo("SMTP unavailable");
    }

    @Test
    void shouldMarkNotificationAndOutboxSentAfterEveryChannelIsTerminal() throws Exception {
        NotificationOutbox outbox = outbox(15L, OutboxState.PUBLISHED);
        Notification notification = notification(25L, NotificationState.SCHEDULED);
        NotificationDelivery fcmDelivery = delivery(15L, NotificationDeliveryChannel.FCM);
        NotificationDelivery emailDelivery = delivery(15L, NotificationDeliveryChannel.EMAIL);
        fcmDelivery.setState(NotificationDeliveryState.SENT);
        fcmDelivery.setSentAt(OffsetDateTime.now().minusSeconds(1));
        emailDelivery.setState(NotificationDeliveryState.SENT);
        emailDelivery.setSentAt(OffsetDateTime.now());
        when(outboxRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(outbox));
        when(notificationRepository.findById(25L)).thenReturn(Optional.of(notification));
        when(deliveryRepository.findAllByOutboxIdOrderByChannelAsc(15L))
                .thenReturn(List.of(fcmDelivery, emailDelivery));

        service.complete(15L);

        assertThat(outbox.getState()).isEqualTo(OutboxState.SENT);
        assertThat(outbox.getSentAt()).isNotNull();
        assertThat(notification.getState()).isEqualTo(NotificationState.SENT);
        assertThat(notification.getSentAt()).isEqualTo(outbox.getSentAt());
    }

    private NotificationOutbox outbox(Long id, OutboxState state) throws Exception {
        return NotificationOutbox.builder()
                .id(id)
                .notificationId(id + 10)
                .type(NotificationType.INFO)
                .payload(objectMapper.writeValueAsString(NotificationDTO.builder()
                        .id(id + 10)
                        .userId(1L)
                        .type(NotificationType.INFO)
                        .message("Test notification")
                        .emailNotifications(true)
                        .userEmail("user@example.test")
                        .fcmTokens(List.of("fcm-token"))
                        .build()))
                .createdAt(OffsetDateTime.now())
                .state(state)
                .build();
    }

    private Notification notification(Long id, NotificationState state) {
        return Notification.builder().id(id).state(state).build();
    }

    private NotificationDelivery delivery(Long outboxId, NotificationDeliveryChannel channel) {
        return NotificationDelivery.builder()
                .id(outboxId + channel.ordinal())
                .outboxId(outboxId)
                .channel(channel)
                .state(NotificationDeliveryState.PENDING)
                .build();
    }
}
