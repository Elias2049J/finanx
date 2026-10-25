package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.outbox.NotificationDelivery;
import com.elias.mismonedas.notifications.outbox.NotificationDeliveryChannel;
import com.elias.mismonedas.notifications.outbox.NotificationDeliveryState;
import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationDeliveryRepository;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryTransactionServiceTest {

    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private NotificationDeliveryRepository deliveryRepository;
    @Mock private NotificationRepository notificationRepository;

    private NotificationDeliveryTransactionService service;

    @BeforeEach
    void setUp() {
        service = new NotificationDeliveryTransactionService(
                outboxRepository, deliveryRepository, notificationRepository);
    }

    @Test
    void shouldClaimPendingDeliveryWithStableKeyAndIncrementAttempt() {
        NotificationOutbox outbox = outbox(10L, OutboxState.PUBLISHED);
        NotificationDelivery delivery = delivery(
                10L, NotificationDeliveryChannel.EMAIL, NotificationDeliveryState.PENDING, 0);
        delivery.setIdempotencyKey("notification-outbox:10:EMAIL");
        when(outboxRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(outbox));
        when(deliveryRepository.findByOutboxIdAndChannelForUpdate(10L, NotificationDeliveryChannel.EMAIL))
                .thenReturn(Optional.of(delivery));
        when(notificationRepository.findById(20L))
                .thenReturn(Optional.of(notification(20L, NotificationState.SCHEDULED)));

        var claim = service.claim(10L, NotificationDeliveryChannel.EMAIL);

        assertThat(claim).isPresent();
        assertThat(claim.get().idempotencyKey()).isEqualTo("notification-outbox:10:EMAIL");
        assertThat(claim.get().attemptCount()).isEqualTo(1);
        assertThat(delivery.getState()).isEqualTo(NotificationDeliveryState.SENDING);
        assertThat(delivery.getSendingStartedAt()).isNotNull();
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void shouldNotClaimDeliveryWhileAnotherWorkerOwnsTheActiveLease() {
        NotificationOutbox outbox = outbox(11L, OutboxState.PUBLISHED);
        NotificationDelivery delivery = delivery(
                11L, NotificationDeliveryChannel.FCM, NotificationDeliveryState.SENDING, 1);
        delivery.setIdempotencyKey("notification-outbox:11:FCM");
        delivery.setSendingStartedAt(OffsetDateTime.now());
        when(outboxRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(outbox));
        when(deliveryRepository.findByOutboxIdAndChannelForUpdate(11L, NotificationDeliveryChannel.FCM))
                .thenReturn(Optional.of(delivery));

        var claim = service.claim(11L, NotificationDeliveryChannel.FCM);

        assertThat(claim).isEmpty();
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getState()).isEqualTo(NotificationDeliveryState.SENDING);
    }

    @Test
    void shouldReclaimAnExpiredLeaseWithoutChangingTheIdempotencyKey() {
        NotificationOutbox outbox = outbox(12L, OutboxState.PUBLISHED);
        NotificationDelivery delivery = delivery(
                12L, NotificationDeliveryChannel.FCM, NotificationDeliveryState.SENDING, 1);
        delivery.setIdempotencyKey("notification-outbox:12:FCM");
        delivery.setSendingStartedAt(OffsetDateTime.now().minusMinutes(2));
        when(outboxRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(outbox));
        when(deliveryRepository.findByOutboxIdAndChannelForUpdate(12L, NotificationDeliveryChannel.FCM))
                .thenReturn(Optional.of(delivery));

        var claim = service.claim(12L, NotificationDeliveryChannel.FCM);

        assertThat(claim).isPresent();
        assertThat(claim.get().idempotencyKey()).isEqualTo("notification-outbox:12:FCM");
        assertThat(claim.get().attemptCount()).isEqualTo(2);
        assertThat(delivery.getState()).isEqualTo(NotificationDeliveryState.SENDING);
        assertThat(delivery.getSendingStartedAt()).isAfter(OffsetDateTime.now().minusSeconds(5));
    }

    private NotificationOutbox outbox(Long id, OutboxState state) {
        return NotificationOutbox.builder()
                .id(id)
                .notificationId(id + 10)
                .state(state)
                .build();
    }

    private Notification notification(Long id, NotificationState state) {
        return Notification.builder().id(id).state(state).build();
    }

    private NotificationDelivery delivery(
            Long outboxId,
            NotificationDeliveryChannel channel,
            NotificationDeliveryState state,
            int attemptCount
    ) {
        return NotificationDelivery.builder()
                .id(outboxId)
                .outboxId(outboxId)
                .channel(channel)
                .state(state)
                .attemptCount(attemptCount)
                .build();
    }
}
