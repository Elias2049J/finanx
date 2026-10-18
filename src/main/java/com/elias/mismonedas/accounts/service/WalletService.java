package com.elias.mismonedas.accounts.service;

import com.elias.mismonedas.accounts.dto.wallet.WalletRequest;
import com.elias.mismonedas.accounts.dto.wallet.WalletResponse;
import com.elias.mismonedas.accounts.domain.entity.Wallet;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

public interface WalletService {

    WalletResponse create(WalletRequest request);

    WalletResponse findById(Long id);

    Wallet getOwned(Long id);

    PageResponse<WalletResponse> list(Pageable pageable);

    void disable(Long id);

    WalletResponse getOrCreatePrimary(Long userId, String currencyCode);
}
