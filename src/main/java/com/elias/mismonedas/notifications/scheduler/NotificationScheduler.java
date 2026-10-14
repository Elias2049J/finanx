package com.elias.mismonedas.notifications.scheduler;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.port.NotificationPort;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.dto.notification.NotificationDTO;
import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private static final int RETENTION_DAYS = 90;

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final List<NotificationPort> notificationPorts;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processOutbox() {
        List<NotificationOutbox> pending = outboxRepository.findAllByState(OutboxState.PENDING);

        for (NotificationOutbox entry : pending) {
            try {
                NotificationDTO dto = objectMapper.readValue(entry.getPayload(), NotificationDTO.class);
                notificationPorts.forEach(port -> port.send(dto));

                entry.setState(OutboxState.SENT);
                entry.setSentAt(OffsetDateTime.now());

                if (entry.getNotificationId() != null) {
                    notificationRepository.findById(entry.getNotificationId()).ifPresentOrElse(n -> {
                        n.setState(NotificationState.SENT);
                        n.setSentAt(entry.getSentAt());
                        notificationRepository.save(n);
                    }, () -> log.warn("Outbox id={} apunta a notificationId={} inexistente", entry.getId(), entry.getNotificationId()));
                } else {
                    log.warn("Outbox id={} no tiene notificationId; no se actualiza entidad Notification", entry.getId());
                }

            } catch (Exception e) {
                boolean isTransient = e instanceof RuntimeException
                        && e.getCause() instanceof FirebaseMessagingException;
                if (isTransient && entry.getCreatedAt().plusMinutes(30).isAfter(OffsetDateTime.now())) {
                    log.warn("Error transitorio FCM, outbox id={} se mantiene PENDING para re-intento",
                            entry.getId());
                    continue;
                }
                entry.setState(OutboxState.FAILED);
                log.error("Error enviando outbox id={} notificationId={}", entry.getId(), entry.getNotificationId(), e);
            }

            outboxRepository.save(entry);
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void purgeOldEntries() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RETENTION_DAYS);

        long outboxDeleted = outboxRepository.deleteByStateInAndCreatedAtBefore(
                List.of(OutboxState.SENT, OutboxState.FAILED), cutoff);
        long notificationsDeleted = notificationRepository.deleteByStateInAndCreatedAtBefore(
                List.of(NotificationState.READ, NotificationState.SENT, NotificationState.DISCARD), cutoff);

        if (outboxDeleted > 0 || notificationsDeleted > 0) {
            log.info("Retención: eliminados {} outbox y {} notificaciones anteriores a {}",
                    outboxDeleted, notificationsDeleted, cutoff);
        }
    }
}
