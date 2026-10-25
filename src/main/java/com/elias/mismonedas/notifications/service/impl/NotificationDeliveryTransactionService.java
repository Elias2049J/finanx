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
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryTransactionService {

    private static final Duration SENDING_LEASE = Duration.ofMinutes(1);

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public Optional<DeliveryClaim> claim(Long outboxId, NotificationDeliveryChannel channel) {
        NotificationOutbox outbox = outboxRepository.findByIdForUpdate(outboxId)
                .orElseThrow(() -> new EntityNotFoundException("Notification outbox not found with id " + outboxId));
        if (isTerminal(outbox.getState()) || outbox.getState() == OutboxState.FAILED) {
            return Optional.empty();
        }

        NotificationDelivery delivery = deliveryRepository.findByOutboxIdAndChannelForUpdate(outboxId, channel)
                .orElseThrow(() -> new IllegalStateException(
                        "No delivery record for outboxId=" + outboxId + " channel=" + channel));
        if (delivery.getState() == NotificationDeliveryState.SENT
                || delivery.getState() == NotificationDeliveryState.SKIPPED) {
            return Optional.empty();
        }

        Notification notification = findNotification(outbox);
        if (notification != null && (notification.getState() == NotificationState.DISCARD
                || notification.getState() == NotificationState.SENT)) {
            delivery.setState(NotificationDeliveryState.SKIPPED);
            delivery.setSendingStartedAt(null);
            return Optional.empty();
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (delivery.getState() == NotificationDeliveryState.SENDING
                && delivery.getSendingStartedAt() != null
                && delivery.getSendingStartedAt().isAfter(now.minus(SENDING_LEASE))) {
            return Optional.empty();
        }

        String idempotencyKey = delivery.getIdempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalStateException(
                    "Missing idempotency key for outboxId=" + outboxId + " channel=" + channel);
        }
        delivery.setState(NotificationDeliveryState.SENDING);
        delivery.setSendingStartedAt(now);
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        return Optional.of(new DeliveryClaim(idempotencyKey, delivery.getAttemptCount()));
    }

    @Transactional
    public boolean markSent(Long outboxId, NotificationDeliveryChannel channel, int claimedAttempt) {
        NotificationDelivery delivery = getDeliveryForUpdate(outboxId, channel);
        if (!ownsClaim(delivery, claimedAttempt)) {
            return false;
        }

        delivery.setState(NotificationDeliveryState.SENT);
        delivery.setSentAt(OffsetDateTime.now());
        delivery.setSendingStartedAt(null);
        delivery.setErrorMessage(null);
        return true;
    }

    @Transactional
    public boolean markFailed(
            Long outboxId,
            NotificationDeliveryChannel channel,
            int claimedAttempt,
            String errorMessage
    ) {
        NotificationDelivery delivery = getDeliveryForUpdate(outboxId, channel);
        if (!ownsClaim(delivery, claimedAttempt)) {
            return false;
        }

        delivery.setState(NotificationDeliveryState.FAILED);
        delivery.setSendingStartedAt(null);
        delivery.setErrorMessage(abbreviate(errorMessage, 255));
        return true;
    }

    private NotificationDelivery getDeliveryForUpdate(Long outboxId, NotificationDeliveryChannel channel) {
        return deliveryRepository.findByOutboxIdAndChannelForUpdate(outboxId, channel)
                .orElseThrow(() -> new IllegalStateException(
                        "No delivery record for outboxId=" + outboxId + " channel=" + channel));
    }

    private Notification findNotification(NotificationOutbox outbox) {
        if (outbox.getNotificationId() == null) {
            return null;
        }
        return notificationRepository.findById(outbox.getNotificationId()).orElse(null);
    }

    private boolean ownsClaim(NotificationDelivery delivery, int claimedAttempt) {
        return delivery.getState() == NotificationDeliveryState.SENDING
                && delivery.getAttemptCount() == claimedAttempt;
    }

    private boolean isTerminal(OutboxState state) {
        return state == OutboxState.SENT || state == OutboxState.SKIPPED;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record DeliveryClaim(String idempotencyKey, int attemptCount) {
    }
}
