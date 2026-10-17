package com.elias.mismonedas.document.scheduler;

import com.elias.mismonedas.document.outbox.ReceiptScanOutbox;
import com.elias.mismonedas.document.outbox.ReceiptScanOutboxState;
import com.elias.mismonedas.document.outbox.persistence.ReceiptScanOutboxRepository;
import com.elias.mismonedas.document.port.ReceiptScanJobPublisher;
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
public class ReceiptScanScheduler {

    private static final int RETENTION_DAYS = 30;

    private final ReceiptScanOutboxRepository outboxRepository;
    private final ReceiptScanJobPublisher outboxPublisher;

    @Scheduled(fixedDelay = 5_000)
    @SchedulerLock(name = "receiptScanScheduler.processOutbox", lockAtMostFor = "PT2M", lockAtLeastFor = "PT2S")
    public void processOutbox() {
        List<ReceiptScanOutbox> pending = outboxRepository.findTop50ByStateOrderByIdAsc(ReceiptScanOutboxState.PENDING);
        for (ReceiptScanOutbox entry : pending) {
            try {
                outboxPublisher.publish(entry.getId());
                outboxRepository.markPublishedIfPending(
                        entry.getId(), ReceiptScanOutboxState.PENDING,
                        ReceiptScanOutboxState.PUBLISHED, OffsetDateTime.now());
            } catch (RuntimeException ex) {
                log.warn("Receipt scan outbox id={} remains PENDING because the broker did not confirm publishing",
                        entry.getId(), ex);
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(name = "receiptScanScheduler.purgeOldEntries", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    @Transactional
    public void purgeOldEntries() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(90);
        long deleted = outboxRepository.deleteByStateInAndCreatedAtBefore(
                List.of(ReceiptScanOutboxState.PUBLISHED, ReceiptScanOutboxState.FAILED), cutoff);
        if (deleted > 0) {
            log.info("Retención: eliminados {} outbox de escaneo anteriores a {}", deleted, cutoff);
        }
    }
}
