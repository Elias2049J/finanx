package com.elias.mismonedas.wallets.listener;

import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletionOrder;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletUserDeletionListener {

    private final WalletRepository walletRepository;

    @EventListener
    @Order(UserDeletionOrder.WALLETS)
    public void onUserDeleted(UserDeletedEvent event) {
        walletRepository.deleteAllByUser_Id(event.userId());
        log.info("Deleted wallets for user {}", event.userId());
    }
}
