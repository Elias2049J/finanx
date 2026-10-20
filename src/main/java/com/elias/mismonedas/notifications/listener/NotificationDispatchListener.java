package com.elias.mismonedas.notifications.listener;

import com.elias.mismonedas.notifications.domain.event.NotificationOutboxCreatedEvent;
import com.elias.mismonedas.notifications.service.impl.NotificationOutboxDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationDispatchListener {

    private final NotificationOutboxDispatcher outboxDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxCreated(NotificationOutboxCreatedEvent event) {
        outboxDispatcher.dispatchAsync(event.outboxId());
    }
}
