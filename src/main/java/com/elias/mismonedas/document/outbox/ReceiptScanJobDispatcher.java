package com.elias.mismonedas.document.outbox;

import com.elias.mismonedas.document.outbox.persistence.ReceiptScanOutboxRepository;
import com.elias.mismonedas.document.port.ReceiptScanJobPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Publica un trabajo de escaneo del outbox de inmediato y lo marca como
 * PUBLISHED dentro de su propia transacción.
 *
 * <p>Se invoca desde el hook {@code afterCommit} de {@code startScan}: en ese
 * punto la transacción original ya confirmó pero sus recursos siguen ligados al
 * hilo, por lo que una llamada normal al repositorio se uniría a una transacción
 * terminada (falla con "No active transaction"). Al ejecutarse como bean aparte
 * con {@link Propagation#REQUIRES_NEW} se suspende esa transacción muerta y se
 * abre una nueva. El scheduler del outbox queda como red de seguridad para
 * reintentos si el broker no confirma.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReceiptScanJobDispatcher {

    private final ReceiptScanJobPublisher outboxPublisher;
    private final ReceiptScanOutboxRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(Long outboxId) {
        outboxPublisher.publish(outboxId);
        outboxRepository.markPublishedIfPending(
                outboxId, ReceiptScanOutboxState.PENDING,
                ReceiptScanOutboxState.PUBLISHED, OffsetDateTime.now());
    }
}
