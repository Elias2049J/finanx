package com.elias.mismonedas.document.outbox;

import com.elias.mismonedas.document.outbox.persistence.ReceiptScanOutboxRepository;
import com.elias.mismonedas.document.port.ReceiptScanJobPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;












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
