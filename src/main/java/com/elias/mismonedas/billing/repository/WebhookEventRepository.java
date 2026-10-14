package com.elias.mismonedas.billing.repository;

import com.elias.mismonedas.billing.domain.entity.WebhookEvent;
import com.elias.mismonedas.billing.domain.enums.WebhookEventState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    boolean existsByExternalEventId(String externalEventId);

    Optional<WebhookEvent> findByExternalEventId(String externalEventId);

    /**
     * Events that still need work: FAILED (transient errors worth retrying) or
     * RECEIVED for longer than {@code cutoff} (a crash between persisting and
     * processing). Ordered oldest-first so the backlog drains in arrival order.
     */
    @Query("""
            select w from WebhookEvent w
            where w.state = :failed
               or (w.state = :received and w.receivedAt < :cutoff)
            order by w.receivedAt asc
            """)
    List<WebhookEvent> findReconcilable(@Param("received") WebhookEventState received,
                                        @Param("failed") WebhookEventState failed,
                                        @Param("cutoff") OffsetDateTime cutoff,
                                        Pageable pageable);
}
