package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.wallet.WalletRequest;
import com.elias.mismonedas.dto.wallet.WalletResponse;

import java.util.List;

public interface WalletService {

    WalletResponse create(Long userId, WalletRequest request);

    WalletResponse findById(Long id);

    List<WalletResponse> findAllByUser(Long userId);

    WalletResponse update(Long id, WalletRequest request);

    void disable(Long id);

    WalletResponse getOrCreatePrimary(Long userId, String currencyCode);
}
