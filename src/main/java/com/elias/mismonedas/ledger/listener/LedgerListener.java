package com.elias.mismonedas.ledger.listener;

import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletionOrder;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import org.springframework.core.annotation.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerListener {

    private final TransactionRepository transactionRepository;

    @EventListener
    @Order(UserDeletionOrder.LEDGER)
    public void onUserDeleted(UserDeletedEvent event) {
        transactionRepository.deleteAllByWallet_User_Id(event.userId());
        log.info("Deleted transactions for user {}", event.userId());
    }
}
