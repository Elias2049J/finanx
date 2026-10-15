package com.elias.mismonedas.document.outbox.persistence;

import com.elias.mismonedas.document.outbox.ReceiptScanOutbox;
import com.elias.mismonedas.document.outbox.ReceiptScanOutboxState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface ReceiptScanOutboxRepository extends JpaRepository<ReceiptScanOutbox, Long> {

    List<ReceiptScanOutbox> findTop50ByStateOrderByIdAsc(ReceiptScanOutboxState state);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update ReceiptScanOutbox o set o.state = :published, o.publishedAt = :now "
            + "where o.id = :id and o.state = :pending")
    int markPublishedIfPending(
            @Param("id") Long id,
            @Param("pending") ReceiptScanOutboxState pending,
            @Param("published") ReceiptScanOutboxState published,
            @Param("now") OffsetDateTime now
    );

    long deleteByStateInAndCreatedAtBefore(Collection<ReceiptScanOutboxState> states, OffsetDateTime cutoff);
}
