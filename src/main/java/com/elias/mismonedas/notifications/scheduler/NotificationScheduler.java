package com.elias.mismonedas.notifications.scheduler;

import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.port.NotificationOutboxPublisher;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.amqp.AmqpException;
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
    private final NotificationOutboxPublisher outboxPublisher;

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "notificationScheduler.processOutbox", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void processOutbox() {
        List<NotificationOutbox> pending = outboxRepository.findTop50ByStateOrderByIdAsc(OutboxState.PENDING);
        for (NotificationOutbox entry : pending) {
            try {
                outboxPublisher.publish(entry.getId());
                int updated = outboxRepository.markPublishedIfPending(
                        entry.getId(), OutboxState.PENDING, OutboxState.PUBLISHED);
                if (updated == 0) {
                    log.debug("Outbox id={} changed state while publishing; keeping current state", entry.getId());
                }
            } catch (AmqpException ex) {
                log.warn("Outbox id={} remains PENDING because the broker did not confirm publishing", entry.getId(), ex);
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(name = "notificationScheduler.purgeOldEntries", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    @Transactional
    public void purgeOldEntries() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RETENTION_DAYS);

        long outboxDeleted = outboxRepository.deleteByStateInAndCreatedAtBefore(
                List.of(OutboxState.SENT, OutboxState.FAILED, OutboxState.SKIPPED), cutoff);
        long notificationsDeleted = notificationRepository.deleteByStateInAndCreatedAtBefore(
                List.of(NotificationState.READ, NotificationState.SENT, NotificationState.DISCARD), cutoff);

        if (outboxDeleted > 0 || notificationsDeleted > 0) {
            log.info("Retención: eliminados {} outbox y {} notificaciones anteriores a {}",
                    outboxDeleted, notificationsDeleted, cutoff);
        }
    }
}
