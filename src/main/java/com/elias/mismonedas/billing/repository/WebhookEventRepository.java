package com.elias.mismonedas.billing.repository;

import com.elias.mismonedas.billing.domain.entity.WebhookEvent;
import com.elias.mismonedas.billing.domain.enums.WebhookEventState;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findByExternalEventId(String externalEventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WebhookEvent w where w.id = :id")
    Optional<WebhookEvent> findByIdForUpdate(@Param("id") Long id);

    /**
     * Events persisted but not successfully handed to the broker before a crash
     * or a broker outage. Ordered oldest-first so recovery drains arrival order.
     */
    @Query("""
            select w from WebhookEvent w
            where w.state = :received
              and w.receivedAt < :cutoff
            order by w.receivedAt asc
            """)
    List<WebhookEvent> findStaleReceived(@Param("received") WebhookEventState received,
                                         @Param("cutoff") OffsetDateTime cutoff,
                                         Pageable pageable);
}
