package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryService {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Optional<NotificationDTO> prepare(Long outboxId, List<NotificationPort> notificationPorts) {
        NotificationOutbox outbox = getOutboxForUpdate(outboxId);
        if (isTerminal(outbox.getState())) {
            return Optional.empty();
        }

        List<NotificationDelivery> deliveries = ensureChannelDeliveries(outboxId, notificationPorts);
        Notification notification = findNotification(outbox);

        if (notification != null && notification.getState() == NotificationState.DISCARD) {
            markPendingDeliveriesSkipped(deliveries);
            outbox.setState(OutboxState.SKIPPED);
            log.info("Skipping discarded notification outbox id={}", outboxId);
            return Optional.empty();
        }

        if (notification != null && notification.getState() == NotificationState.SENT) {
            markPendingDeliveriesSkipped(deliveries);
            outbox.setState(OutboxState.SENT);
            outbox.setSentAt(notification.getSentAt() != null ? notification.getSentAt() : OffsetDateTime.now());
            return Optional.empty();
        }

        NotificationDTO dto = readPayload(outbox);

        if (outbox.getState() == OutboxState.FAILED) {
            for (NotificationDelivery delivery : deliveries) {
                if (delivery.getState() == NotificationDeliveryState.FAILED) {
                    delivery.setState(NotificationDeliveryState.PENDING);
                    delivery.setErrorMessage(null);
                }
            }
            outbox.setState(OutboxState.PUBLISHED);
        }

        if (deliveries.isEmpty()) {
            outbox.setState(OutboxState.SENT);
            OffsetDateTime sentAt = OffsetDateTime.now();
            outbox.setSentAt(sentAt);
            markNotificationSent(notification, sentAt);
            return Optional.empty();
        }

        return Optional.of(dto);
    }

    @Transactional
    public void deliverChannel(Long outboxId, NotificationPort port, NotificationDTO dto) {
        NotificationOutbox outbox = getOutboxForUpdate(outboxId);
        if (isTerminal(outbox.getState())) {
            return;
        }

        NotificationDelivery delivery = deliveryRepository
                .findByOutboxIdAndChannelForUpdate(outboxId, port.channel())
                .orElseThrow(() -> new IllegalStateException(
                        "No delivery record for outboxId=" + outboxId + " channel=" + port.channel()));
        if (delivery.getState() == NotificationDeliveryState.SENT
                || delivery.getState() == NotificationDeliveryState.SKIPPED) {
            return;
        }

        Notification notification = findNotification(outbox);
        if (notification != null && notification.getState() == NotificationState.DISCARD) {
            delivery.setState(NotificationDeliveryState.SKIPPED);
            return;
        }
        if (notification != null && notification.getState() == NotificationState.SENT) {
            delivery.setState(NotificationDeliveryState.SKIPPED);
            return;
        }

        port.send(dto);
        delivery.setState(NotificationDeliveryState.SENT);
        delivery.setSentAt(OffsetDateTime.now());
        delivery.setErrorMessage(null);
    }

    @Transactional
    public void complete(Long outboxId) {
        NotificationOutbox outbox = getOutboxForUpdate(outboxId);
        if (isTerminal(outbox.getState())) {
            return;
        }

        List<NotificationDelivery> deliveries = deliveryRepository.findAllByOutboxIdOrderByChannelAsc(outboxId);
        if (deliveries.stream().anyMatch(delivery ->
                delivery.getState() == NotificationDeliveryState.PENDING
                        || delivery.getState() == NotificationDeliveryState.FAILED)) {
            throw new IllegalStateException("Notification outbox still has undelivered channels id=" + outboxId);
        }

        Notification notification = findNotification(outbox);
        if (notification != null && notification.getState() == NotificationState.DISCARD) {
            outbox.setState(OutboxState.SKIPPED);
            return;
        }

        boolean allSkipped = !deliveries.isEmpty() && deliveries.stream()
                .allMatch(delivery -> delivery.getState() == NotificationDeliveryState.SKIPPED);
        outbox.setState(allSkipped ? OutboxState.SKIPPED : OutboxState.SENT);
        if (!allSkipped) {
            OffsetDateTime sentAt = OffsetDateTime.now();
            outbox.setSentAt(sentAt);
            markNotificationSent(notification, sentAt);
        }
    }

    @Transactional
    public void markFailed(Long outboxId, String errorMessage) {
        NotificationOutbox outbox = getOutboxForUpdate(outboxId);
        if (isTerminal(outbox.getState())) {
            return;
        }

        outbox.setState(OutboxState.FAILED);
        for (NotificationDelivery delivery : deliveryRepository.findAllByOutboxIdOrderByChannelAsc(outboxId)) {
            if (delivery.getState() == NotificationDeliveryState.PENDING) {
                delivery.setState(NotificationDeliveryState.FAILED);
                delivery.setErrorMessage(abbreviate(errorMessage, 255));
            }
        }
    }

    private List<NotificationDelivery> ensureChannelDeliveries(
            Long outboxId,
            List<NotificationPort> notificationPorts
    ) {
        List<NotificationDelivery> existing = new ArrayList<>(
                deliveryRepository.findAllByOutboxIdOrderByChannelAsc(outboxId));
        Set<NotificationDeliveryChannel> existingChannels = existing.stream()
                .map(NotificationDelivery::getChannel)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(NotificationDeliveryChannel.class)));

        List<NotificationDelivery> missing = new ArrayList<>();
        for (NotificationPort port : notificationPorts) {
            if (existingChannels.add(port.channel())) {
                missing.add(NotificationDelivery.builder()
                        .outboxId(outboxId)
                        .channel(port.channel())
                        .state(NotificationDeliveryState.PENDING)
                        .createdAt(OffsetDateTime.now())
                        .build());
            }
        }
        if (!missing.isEmpty()) {
            deliveryRepository.saveAll(missing);
            existing.addAll(missing);
        }
        return existing;
    }

    private void markPendingDeliveriesSkipped(List<NotificationDelivery> deliveries) {
        deliveries.stream()
                .filter(delivery -> delivery.getState() == NotificationDeliveryState.PENDING
                        || delivery.getState() == NotificationDeliveryState.FAILED)
                .forEach(delivery -> delivery.setState(NotificationDeliveryState.SKIPPED));
    }

    private NotificationOutbox getOutboxForUpdate(Long outboxId) {
        return outboxRepository.findByIdForUpdate(outboxId)
                .orElseThrow(() -> new EntityNotFoundException("Notification outbox not found with id " + outboxId));
    }

    private Notification findNotification(NotificationOutbox outbox) {
        if (outbox.getNotificationId() == null) {
            return null;
        }
        return notificationRepository.findById(outbox.getNotificationId()).orElse(null);
    }

    private NotificationDTO readPayload(NotificationOutbox outbox) {
        try {
            return objectMapper.readValue(outbox.getPayload(), NotificationDTO.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not deserialize notification outbox id=" + outbox.getId(), ex);
        }
    }

    private void markNotificationSent(Notification notification, OffsetDateTime sentAt) {
        if (notification != null && notification.getState() == NotificationState.SCHEDULED) {
            notification.setState(NotificationState.SENT);
            notification.setSentAt(sentAt);
        }
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
}
