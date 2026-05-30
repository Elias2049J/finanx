package com.elias.finanx.outbox.persistence;

import com.elias.finanx.outbox.NotificationOutbox;
import com.elias.finanx.outbox.OutboxState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    List<NotificationOutbox> findAllByState(OutboxState state);
}
