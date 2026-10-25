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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private NotificationDeliveryRepository deliveryRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationDeliveryTransactionService deliveryTransactions;
    @Mock private NotificationPort fcmPort;
    @Mock private NotificationPort emailPort;

    private NotificationDeliveryService service;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        service = new NotificationDeliveryService(
                outboxRepository, deliveryRepository, notificationRepository, objectMapper, deliveryTransactions);
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
    void shouldNotImmediatelyRetryOutboxThatIsWaitingForScheduledRetry() throws Exception {
        NotificationOutbox outbox = outbox(18L, OutboxState.FAILED);
        outbox.setNextRetryAt(OffsetDateTime.now().plusMinutes(1));
        when(outboxRepository.findByIdForUpdate(18L)).thenReturn(Optional.of(outbox));

        Optional<NotificationDTO> prepared = service.prepare(18L, List.of(fcmPort));

        assertThat(prepared).isEmpty();
        verify(deliveryRepository, never()).findAllByOutboxIdOrderByChannelAsc(18L);
        assertThat(outbox.getState()).isEqualTo(OutboxState.FAILED);
    }

    @Test
    void shouldSendOutsideTransactionUsingStableKeyAndRecordTheClaimedAttempt() throws Exception {
        when(fcmPort.channel()).thenReturn(NotificationDeliveryChannel.FCM);
        NotificationDTO dto = NotificationDTO.builder().id(22L).userId(1L).build();
        var claim = new NotificationDeliveryTransactionService.DeliveryClaim("stable-key", 3);
        when(deliveryTransactions.claim(12L, NotificationDeliveryChannel.FCM))
                .thenReturn(Optional.of(claim), Optional.empty());
        when(deliveryTransactions.markSent(12L, NotificationDeliveryChannel.FCM, 3)).thenReturn(true);

        service.deliverChannel(12L, fcmPort, dto);
        service.deliverChannel(12L, fcmPort, dto);

        verify(fcmPort).send(dto, "stable-key");
        verify(deliveryTransactions).markSent(12L, NotificationDeliveryChannel.FCM, 3);
    }

    @Test
    void shouldRecordFailedAttemptAndRethrowSoBrokerRetryCanRetryIt() throws Exception {
        when(emailPort.channel()).thenReturn(NotificationDeliveryChannel.EMAIL);
        NotificationDTO dto = NotificationDTO.builder().id(23L).userId(1L).build();
        var claim = new NotificationDeliveryTransactionService.DeliveryClaim("email-key", 1);
        when(deliveryTransactions.claim(13L, NotificationDeliveryChannel.EMAIL)).thenReturn(Optional.of(claim));
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(emailPort).send(dto, "email-key");

        assertThatThrownBy(() -> service.deliverChannel(13L, emailPort, dto))
                .isInstanceOf(IllegalStateException.class);

        verify(deliveryTransactions).markFailed(
                13L, NotificationDeliveryChannel.EMAIL, 1, "SMTP unavailable");
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
    void shouldMarkAnAbandonedSendingDeliveryFailedAfterRetryExhaustion() throws Exception {
        NotificationOutbox outbox = outbox(16L, OutboxState.PUBLISHED);
        NotificationDelivery sending = delivery(16L, NotificationDeliveryChannel.EMAIL);
        sending.setState(NotificationDeliveryState.SENDING);
        sending.setSendingStartedAt(OffsetDateTime.now().minusMinutes(2));
        when(outboxRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(outbox));
        when(deliveryRepository.findAllByOutboxIdOrderByChannelAsc(16L)).thenReturn(List.of(sending));

        service.markFailed(16L, "SMTP worker stopped");

        assertThat(outbox.getState()).isEqualTo(OutboxState.FAILED);
        assertThat(sending.getState()).isEqualTo(NotificationDeliveryState.FAILED);
        assertThat(sending.getSendingStartedAt()).isNull();
        assertThat(sending.getErrorMessage()).isEqualTo("SMTP worker stopped");
    }

    @Test
    void shouldNotCompleteOutboxWhileAChannelClaimIsStillSending() throws Exception {
        NotificationOutbox outbox = outbox(17L, OutboxState.PUBLISHED);
        NotificationDelivery sending = delivery(17L, NotificationDeliveryChannel.EMAIL);
        sending.setState(NotificationDeliveryState.SENDING);
        when(outboxRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(outbox));
        when(deliveryRepository.findAllByOutboxIdOrderByChannelAsc(17L)).thenReturn(List.of(sending));

        assertThatThrownBy(() -> service.complete(17L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("undelivered channels");
        assertThat(outbox.getState()).isEqualTo(OutboxState.PUBLISHED);
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
