package com.elias.mismonedas.wallets.service.impl;

import com.elias.mismonedas.billing.service.SubscriptionService;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserPreferencesRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.dto.wallet.WalletResponse;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
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

    @Nested
    class SetPrimaryTest {

        private static final long USER_ID = 7L;

        private User currentUser() {
            User user = mock(User.class);
            when(user.getId()).thenReturn(USER_ID);
            when(securityService.getCurrentUser()).thenReturn(user);
            return user;
        }

        private Wallet walletOf(Long id, String currency, boolean primary) {
            return Wallet.builder()
                    .id(id)
                    .currencyCode(currency)
                    .balance(BigDecimal.ZERO)
                    .primary(primary)
                    .build();
        }

        private WalletResponse responseOf(Wallet wallet) {
            return new WalletResponse(
                    wallet.getId(), USER_ID, wallet.getCurrencyCode(), wallet.getBalance(), wallet.isPrimary());
        }

        @Test
        @DisplayName("promueve la billetera pedida y degrada la principal anterior")
        void shouldPromoteTargetAndDemotePrevious() {
            currentUser();
            Wallet previous = walletOf(1L, "PEN", true);
            Wallet target = walletOf(2L, "USD", false);
            WalletResponse expected = responseOf(target);
            expected.setPrimary(true);

            when(walletRepository.findByIdAndUser_Id(2L, USER_ID)).thenReturn(Optional.of(target));
            when(walletRepository.findByUserIdAndPrimaryTrue(USER_ID)).thenReturn(Optional.of(previous));
            when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
            when(walletMapper.toResponse(target)).thenReturn(expected);

            WalletResponse result = service.setPrimary(2L);

            assertThat(previous.isPrimary()).isFalse();
            assertThat(target.isPrimary()).isTrue();
            assertThat(result).isSameAs(expected);
            verify(walletRepository).save(previous);
            verify(walletRepository).save(target);
        }

        @Test
        @DisplayName("si no habia principal, solo promueve la pedida")
        void shouldPromoteWhenNoPrimaryExists() {
            currentUser();
            Wallet target = walletOf(2L, "USD", false);

            when(walletRepository.findByIdAndUser_Id(2L, USER_ID)).thenReturn(Optional.of(target));
            when(walletRepository.findByUserIdAndPrimaryTrue(USER_ID)).thenReturn(Optional.empty());
            when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
            when(walletMapper.toResponse(target)).thenReturn(responseOf(target));

            service.setPrimary(2L);

            assertThat(target.isPrimary()).isTrue();
            verify(walletRepository).save(target);
        }

        @Test
        @DisplayName("es idempotente: si ya es principal no escribe en la BD")
        void shouldBeIdempotentWhenAlreadyPrimary() {
            currentUser();
            Wallet wallet = walletOf(2L, "USD", true);
            WalletResponse expected = responseOf(wallet);

            when(walletRepository.findByIdAndUser_Id(2L, USER_ID)).thenReturn(Optional.of(wallet));
            when(walletMapper.toResponse(wallet)).thenReturn(expected);

            WalletResponse result = service.setPrimary(2L);

            assertThat(result).isSameAs(expected);
            verify(walletRepository, never()).save(any(Wallet.class));
            verify(walletRepository, never()).findByUserIdAndPrimaryTrue(anyLong());
        }

        @Test
        @DisplayName("lanza EntityNotFound si la billetera no es del usuario")
        void shouldThrowWhenWalletIsNotOwned() {
            currentUser();
            when(walletRepository.findByIdAndUser_Id(99L, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setPrimary(99L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("99");

            verify(walletRepository, never()).save(any(Wallet.class));
        }
    }
}
