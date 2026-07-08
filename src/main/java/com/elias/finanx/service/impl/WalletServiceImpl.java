package com.elias.finanx.service.impl;

import com.elias.finanx.dto.wallet.WalletRequest;
import com.elias.finanx.dto.wallet.WalletResponse;
import com.elias.finanx.entity.Wallet;
import com.elias.finanx.entity.User;
import com.elias.finanx.mapper.WalletMapper;
import com.elias.finanx.repository.WalletRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletMapper walletMapper;

    @Override
    @Transactional
    public WalletResponse create(Long userId, WalletRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        Wallet wallet = walletMapper.toEntity(request);
        wallet.setUser(user);
        return walletMapper.toResponse(walletRepository.save(wallet));
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse findById(Long id) {
        return walletMapper.toResponse(walletRepository.findById(id).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletResponse> findAllByUser(Long userId) {
        return walletRepository.findByUserId(userId)
                .stream()
                .map(walletMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public WalletResponse update(Long id, WalletRequest request) {
        Wallet existing = walletRepository.findById(id).orElseThrow();
        walletMapper.updateFromDto(request, existing);
        return walletMapper.toResponse(walletRepository.save(existing));
    }

    @Override
    @Transactional
    public void disable(Long id) {
        Wallet wallet = walletRepository.findById(id).orElseThrow();
        wallet.setPrimary(false);
        walletRepository.save(wallet);
    }

    @Override
    @Transactional
    public WalletResponse getOrCreatePrimary(Long userId, String currencyCode) {
        return walletRepository.findByUserIdAndPrimaryTrue(userId)
                .map(walletMapper::toResponse)
                .orElseGet(() -> {
                    Wallet wallet = Wallet.builder()
                            .user(userRepository.getReferenceById(userId))
                            .currencyCode(currencyCode)
                            .balance(BigDecimal.ZERO)
                            .primary(true)
                            .build();
                    return walletMapper.toResponse(walletRepository.save(wallet));
                });
    }
}
