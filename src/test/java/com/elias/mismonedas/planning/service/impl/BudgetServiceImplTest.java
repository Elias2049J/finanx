package com.elias.mismonedas.planning.service.impl;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.domain.enums.BudgetHealth;
import com.elias.mismonedas.planning.domain.enums.BudgetState;
import com.elias.mismonedas.planning.mapper.BudgetMapper;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.scheduling.service.RecurrenceService;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceImplTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private BudgetMapper budgetMapper;
    @Mock private RecurrenceService recurrenceService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private RecurrenceService rcService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CurrencyConverter currencyConverter;
    @Mock private SecurityService securityService;
    @Mock private WalletRepository walletRepository;

    private BudgetServiceImpl service;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        service = new BudgetServiceImpl(
                budgetRepository, categoryRepository, userRepository, budgetMapper,
                recurrenceService, transactionRepository, rcService, eventPublisher,
                currencyConverter, securityService, walletRepository);

        user = new User();
        user.setId(1L);
        user.setPreferences(UserPreferences.builder().timeZone(TimeZone.AMERICA_LIMA).build());

        category = new Category();
        category.setId(100L);
        category.setUser(user);
    }

    @Test
    void shouldKeepRecheckingOtherBudgetsWhenOneFails() {
        // El primer presupuesto tiene una categoria corrupta (fila legacy o FK
        // rota): antes un fallo asi abortaba el bucle y el usuario se quedaba
        // sin recalcular el resto.
        Budget broken = budget(null, BudgetHealth.NEAR_LIMIT);
        Budget healthy = budget(category, BudgetHealth.NEAR_LIMIT);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(budgetRepository.findAllByUser_IdAndActiveTrueAndState(1L, BudgetState.ACTIVE))
                .thenReturn(List.of(broken, healthy));
        when(transactionRepository.sumAmountsByCurrencyForCategory(
                eq(1L), eq(100L), any(), any(), any()))
                .thenReturn(List.of());

        service.checkAllBudgets(1L);

        // Sin gasto, el presupuesto sano vuelve a OK: el bucle continuo.
        assertThat(healthy.getHealth()).isEqualTo(BudgetHealth.OK);
        assertThat(broken.getHealth()).isEqualTo(BudgetHealth.NEAR_LIMIT);
    }

    private Budget budget(Category category, BudgetHealth health) {
        return Budget.builder()
                .id(category == null ? 1L : 2L)
                .user(user)
                .category(category)
                .alertable(true)
                .percentAlert(80)
                .limitAmount(new BigDecimal("100.00"))
                .currencyCode("PEN")
                .state(BudgetState.ACTIVE)
                .health(health)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
