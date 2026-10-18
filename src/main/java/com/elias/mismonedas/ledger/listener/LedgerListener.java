package com.elias.mismonedas.ledger.listener;

import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** F3/F4: el ledger borra sus propias transacciones cuando se elimina una cuenta. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerListener {

    private final TransactionRepository transactionRepository;

    @EventListener
    public void onUserDeleted(UserDeletedEvent event) {
        // Las transacciones cuelgan de la billetera, no directamente del usuario.
        transactionRepository.deleteAllByWallet_User_Id(event.userId());
        log.info("Deleted transactions for user {}", event.userId());
    }
}
