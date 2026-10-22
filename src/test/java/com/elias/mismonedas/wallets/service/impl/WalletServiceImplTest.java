package com.elias.mismonedas.wallets.service.impl;

import com.elias.mismonedas.billing.service.SubscriptionService;
import com.elias.mismonedas.identity.repository.UserPreferencesRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.wallets.mapper.WalletMapper;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock private WalletRepository walletRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletMapper walletMapper;
    @Mock private SubscriptionService subscriptionService;
    @Mock private UserPreferencesRepository preferencesRepository;
    @Mock private SecurityService securityService;

    private WalletServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WalletServiceImpl(
                walletRepository, userRepository, walletMapper,
                subscriptionService, preferencesRepository, securityService);
    }

    @Nested
    class UpdateBalanceTest {

        @Test
        @DisplayName("acredita el saldo en un ingreso")
        void shouldCreditBalanceOnIncome() {
            when(walletRepository.adjustBalance(10L, 1L, new BigDecimal("50.00"))).thenReturn(1);

            service.updateBalance(1L, 10L, TransactionType.INCOME, new BigDecimal("50.00"));

            verify(walletRepository).adjustBalance(10L, 1L, new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("descuenta el saldo en un gasto")
        void shouldDebitBalanceOnSpent() {
            when(walletRepository.adjustBalance(10L, 1L, new BigDecimal("-50.00"))).thenReturn(1);

            service.updateBalance(1L, 10L, TransactionType.SPENT, new BigDecimal("50.00"));

            verify(walletRepository).adjustBalance(10L, 1L, new BigDecimal("-50.00"));
        }

        @Test
        @DisplayName("no lee la entidad antes de ajustar: el ajuste es atomico en BD")
        void shouldNotLoadWalletEntityBeforeAdjustingBalance() {
            // Leer la entidad y hacer set() abre la puerta a lost updates cuando
            // dos transacciones del mismo wallet compiten por la fila.
            when(walletRepository.adjustBalance(anyLong(), anyLong(), any())).thenReturn(1);

            service.updateBalance(1L, 10L, TransactionType.SPENT, new BigDecimal("50.00"));

            verify(walletRepository, never()).findByIdAndUser_Id(anyLong(), anyLong());
        }

        @Test
        @DisplayName("lanza EntityNotFound si la wallet no existe o no es del usuario")
        void shouldThrowWhenWalletIsNotOwnedByUser() {
            when(walletRepository.adjustBalance(anyLong(), anyLong(), any())).thenReturn(0);

            assertThatThrownBy(() ->
                    service.updateBalance(1L, 10L, TransactionType.SPENT, new BigDecimal("50.00")))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("10");
        }
    }
}
