package com.elias.mismonedas.notifications.scheduler;

import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.elias.mismonedas.notifications.service.impl.NotificationDeliveryService;
import com.elias.mismonedas.notifications.service.impl.NotificationOutboxDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.IntSupplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private static final int RETENTION_DAYS = 90;
    private static final int STALE_RETENTION_DAYS = 365;
    private static final int PURGE_BATCH_SIZE = 500;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationOutboxDispatcher outboxDispatcher;
    private final NotificationDeliveryService deliveryService;

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

        OffsetDateTime now = OffsetDateTime.now();
        List<NotificationOutbox> dueRetries = outboxRepository
                .findTop50ByStateAndNextRetryAtLessThanEqualOrderByIdAsc(OutboxState.FAILED, now);
        int requeued = 0;
        for (NotificationOutbox entry : dueRetries) {
            if (deliveryService.activateScheduledRetry(entry.getId(), now)) {
                requeued++;
            }
        }
        if (requeued > 0) {
            log.info("Notification retries requeued {} of {} due FAILED outbox entries",
                    requeued, dueRetries.size());
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(name = "notificationScheduler.purgeOldEntries", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void purgeOldEntries() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime cutoff = now.minusDays(RETENTION_DAYS);
        OffsetDateTime staleCutoff = now.minusDays(STALE_RETENTION_DAYS);

        long outboxDeleted = deleteInBatches(() -> outboxRepository.deleteBatchByStateInAndCreatedAtBefore(
                List.of(OutboxState.SENT.name(), OutboxState.SKIPPED.name()),
                cutoff,
                PURGE_BATCH_SIZE));
        long staleOutboxDeleted = deleteInBatches(() -> outboxRepository.deleteStaleBatchBefore(
                staleCutoff,
                now,
                PURGE_BATCH_SIZE));
        long notificationsDeleted = deleteInBatches(() -> notificationRepository.deleteBatchByStateInAndCreatedAtBefore(
                List.of(NotificationState.READ.name(), NotificationState.SENT.name(), NotificationState.DISCARD.name()),
                cutoff,
                PURGE_BATCH_SIZE));

        if (outboxDeleted > 0 || staleOutboxDeleted > 0 || notificationsDeleted > 0) {
            log.info("Retención: eliminados {} outbox terminales, {} outbox obsoletos y {} notificaciones",
                    outboxDeleted, staleOutboxDeleted, notificationsDeleted);
        }
    }

    private long deleteInBatches(IntSupplier deleteBatch) {
        long total = 0;
        int deleted;
        do {
            deleted = deleteBatch.getAsInt();
            total += deleted;
        } while (deleted == PURGE_BATCH_SIZE);
        return total;
    }
}
