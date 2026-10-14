package com.elias.mismonedas.notifications.outbox.persistence;

import com.elias.mismonedas.notifications.outbox.NotificationDelivery;
import com.elias.mismonedas.notifications.outbox.NotificationDeliveryChannel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    List<NotificationDelivery> findAllByOutboxIdOrderByChannelAsc(Long outboxId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from NotificationDelivery d
            where d.outboxId = :outboxId and d.channel = :channel
            """)
    Optional<NotificationDelivery> findByOutboxIdAndChannelForUpdate(
            @Param("outboxId") Long outboxId,
            @Param("channel") NotificationDeliveryChannel channel
    );
}
