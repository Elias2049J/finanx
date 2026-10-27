package com.elias.mismonedas.document.listener;

import com.elias.mismonedas.document.repository.ReceiptRepository;
import com.elias.mismonedas.document.outbox.persistence.ReceiptScanOutboxRepository;
import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletionOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ReceiptUserDeletionListener {

    private final ReceiptRepository receiptRepository;
    private final ReceiptScanOutboxRepository receiptScanOutboxRepository;

    @EventListener
    @Order(UserDeletionOrder.RECEIPTS)
    public void onUserDeleted(UserDeletedEvent event) {
        List<Long> receiptIds = receiptRepository.findIdsByUserId(event.userId());
        if (receiptIds.isEmpty()) {
            return;
        }
        receiptScanOutboxRepository.deleteAllByReceiptIdIn(receiptIds);
        receiptRepository.deleteAllRowsByUserId(event.userId());
    }
}
