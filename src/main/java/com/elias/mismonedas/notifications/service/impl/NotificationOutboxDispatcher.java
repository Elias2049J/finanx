package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.port.NotificationOutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Service
@Slf4j
public class NotificationOutboxDispatcher {

    private static final Duration DISPATCH_LEASE = Duration.ofMinutes(2);

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationOutboxPublisher outboxPublisher;
    private final ExecutorService dispatchExecutor;

    public NotificationOutboxDispatcher(
            NotificationOutboxRepository outboxRepository,
            NotificationOutboxPublisher outboxPublisher,
            @Qualifier("notificationOutboxDispatchExecutor") ExecutorService dispatchExecutor
    ) {
        this.outboxRepository = outboxRepository;
        this.outboxPublisher = outboxPublisher;
        this.dispatchExecutor = dispatchExecutor;
    }

    public void dispatchAsync(Long outboxId) {
        try {
            dispatchExecutor.execute(() -> dispatch(outboxId));
        } catch (RejectedExecutionException ex) {
            log.warn("No se pudo despachar de inmediato el outbox id={}; el poller lo reintentará", outboxId, ex);
        }
    }

    public boolean dispatch(Long outboxId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime leaseUntil = now.plus(DISPATCH_LEASE);
        if (outboxRepository.claimIfPending(outboxId, OutboxState.PENDING, now, leaseUntil) == 0) {
            return false;
        }

        try {
            outboxPublisher.publish(outboxId);
        } catch (RuntimeException ex) {
            outboxRepository.releaseClaim(outboxId, OutboxState.PENDING, leaseUntil);
            if (ex instanceof AmqpException) {
                log.warn("Outbox id={} sigue PENDING: el broker no confirmó la publicación", outboxId, ex);
            } else {
                log.error("Outbox id={} falló durante el publish; se libera el claim", outboxId, ex);
            }
            return false;
        }

        if (outboxRepository.markPublishedIfClaimed(
                outboxId, OutboxState.PENDING, OutboxState.PUBLISHED, leaseUntil) == 0) {
            log.debug("Outbox id={} cambió de estado o perdió el claim durante el publish", outboxId);
            return false;
        }
        return true;
    }
}
