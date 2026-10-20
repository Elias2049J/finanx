package com.elias.mismonedas.wallets.listener;

import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.domain.event.TransactionDisabledEvent;
import com.elias.mismonedas.wallets.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletListener {

    private final WalletService walletService;

    @EventListener
    public void on(TransactionDisabledEvent event) {
        TransactionType reversed = event.type() == TransactionType.INCOME
                ? TransactionType.SPENT
                : TransactionType.INCOME;
        walletService.updateBalance(event.userId(), event.walletId(), reversed, event.amount());
    }
}
