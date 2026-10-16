package com.elias.mismonedas.accounts.service.impl;

import com.elias.mismonedas.accounts.domain.entity.Wallet;
import com.elias.mismonedas.accounts.dto.wallet.WalletRequest;
import com.elias.mismonedas.accounts.dto.wallet.WalletResponse;
import com.elias.mismonedas.accounts.mapper.WalletMapper;
import com.elias.mismonedas.accounts.repository.WalletRepository;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import com.elias.mismonedas.accounts.service.WalletService;
import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import com.elias.mismonedas.billing.plan.PlanRequiredException;
import com.elias.mismonedas.billing.service.SubscriptionService;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserPreferencesRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletMapper walletMapper;
    private final SubscriptionService subscriptionService;
    private final UserPreferencesRepository preferencesRepository;
    private final SecurityService securityService;

    @Override
    @Transactional
    public WalletResponse create(WalletRequest request) {
        User user = securityService.getCurrentUser();
        Long userId = user.getId();
        long walletCount = walletRepository.countByUser_Id(userId);

        if (walletCount == 0) {
            if (preferencesRepository.findByUser_Id(userId).isEmpty()) {
                throw new IllegalArgumentException(
                        "Debe crear sus preferencias antes de crear la primera wallet");
            }
            return getOrCreatePrimary(userId, request.getCurrencyCode());
        }

        SubscriptionPlan plan = subscriptionService.getActivePlanFor(userId);
        if (plan != SubscriptionPlan.PREMIUM) {
            throw new PlanRequiredException(
                    new SubscriptionPlan[]{SubscriptionPlan.PREMIUM}, plan);
        }

        Wallet wallet = walletMapper.toEntity(request);
        wallet.setUser(user);
        if (request.isPrimary()) {
            walletRepository.findByUserIdAndPrimaryTrue(userId)
                    .ifPresent(w -> {
                        w.setPrimary(false);
                        walletRepository.save(w);
                    });
        }
        return walletMapper.toResponse(walletRepository.save(wallet));
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse findById(Long id) {
        return walletMapper.toResponse(getOwned(id));
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
    @Transactional(readOnly = true)
    public PageResponse<WalletResponse> list(Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.ASC, "id"));
        return PageResponseFactory.from(walletRepository.findByUserId(userId, stablePage), walletMapper::toResponse);
    }

    @Override
    @Transactional
    public WalletResponse update(Long id, WalletRequest request) {
        Wallet existing = getOwned(id);
        walletMapper.updateFromDto(request, existing);
        return walletMapper.toResponse(walletRepository.save(existing));
    }

    @Override
    @Transactional
    public void disable(Long id) {
        Wallet wallet = getOwned(id);
        wallet.setPrimary(false);
        walletRepository.save(wallet);
    }

    private Wallet getOwned(Long id) {
        Long userId = securityService.getCurrentUser().getId();
        return walletRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + id));
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
