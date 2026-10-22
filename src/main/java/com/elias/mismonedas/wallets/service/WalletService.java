package com.elias.mismonedas.wallets.service;

import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.wallets.dto.wallet.WalletRequest;
import com.elias.mismonedas.wallets.dto.wallet.WalletResponse;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface WalletService {

    WalletResponse create(WalletRequest request);

    WalletResponse findById(Long id);

    Wallet getOwned(Long id);

    Wallet getOwned(Long id, Long userId);

    PageResponse<WalletResponse> list(Pageable pageable);

    /**
     * @deprecated No se expone en ningun endpoint y, si se llamara, dejaria al
     * usuario sin billetera principal: el dashboard de balance filtra por la
     * principal, asi que pasaria a devolver un dashboard vacio. Antes de
     * exponer un borrado hay que decidir a que billetera se promueve.
     */
    @Deprecated
    void disable(Long id);

    WalletResponse setPrimary(Long id);

    WalletResponse getOrCreatePrimary(Long userId, String currencyCode);

    void updateBalance(Long userId, Long walletId, TransactionType type, BigDecimal amount);
}
