package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.port.NotificationOutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Service
@Slf4j
public class NotificationOutboxDispatcher {

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
        if (!isPending(outboxId)) {
            return false;
        }

        try {
            outboxPublisher.publish(outboxId);
        } catch (AmqpException ex) {
            log.warn("Outbox id={} sigue PENDING: el broker no confirmó la publicación", outboxId, ex);
            return false;
        }

        if (outboxRepository.markPublishedIfPending(outboxId, OutboxState.PENDING, OutboxState.PUBLISHED) == 0) {
            log.debug("Outbox id={} cambió de estado durante el publish; se conserva el estado actual", outboxId);
            return false;
        }
        return true;
    }

    private boolean isPending(Long outboxId) {
        Optional<NotificationOutbox> entry = outboxRepository.findById(outboxId);
        return entry.isPresent() && entry.get().getState() == OutboxState.PENDING;
    }
}
