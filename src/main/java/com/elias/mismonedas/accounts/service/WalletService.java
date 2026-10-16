package com.elias.mismonedas.accounts.service;

import com.elias.mismonedas.accounts.dto.wallet.WalletRequest;
import com.elias.mismonedas.accounts.dto.wallet.WalletResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface WalletService {

    WalletResponse create(WalletRequest request);

    WalletResponse findById(Long id);

    List<WalletResponse> findAllByUser(Long userId);

    PageResponse<WalletResponse> list(Pageable pageable);

    WalletResponse update(Long id, WalletRequest request);

    void disable(Long id);

    WalletResponse getOrCreatePrimary(Long userId, String currencyCode);
}
