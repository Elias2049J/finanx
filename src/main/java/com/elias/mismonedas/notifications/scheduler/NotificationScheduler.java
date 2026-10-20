package com.elias.mismonedas.notifications.scheduler;

import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.elias.mismonedas.notifications.service.impl.NotificationOutboxDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
    private final NotificationOutboxDispatcher outboxDispatcher;

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "notificationScheduler.processOutbox", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void processOutbox() {
        List<NotificationOutbox> pending = outboxRepository.findTop50ByStateOrderByIdAsc(OutboxState.PENDING);
        int rescued = 0;
        for (NotificationOutbox entry : pending) {
            if (outboxDispatcher.dispatch(entry.getId())) {
                rescued++;
            }
        }
        if (rescued > 0) {
            log.info("Red de seguridad del outbox: publicadas {} de {} entradas PENDING", rescued, pending.size());
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
