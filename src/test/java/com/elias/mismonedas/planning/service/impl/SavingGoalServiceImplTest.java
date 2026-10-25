package com.elias.mismonedas.planning.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.repository.projection.SavingGoalAggregate;
import com.elias.mismonedas.planning.domain.entity.SavingGoal;
import com.elias.mismonedas.planning.domain.vo.SavingGoalTotals;
import com.elias.mismonedas.planning.dto.saving.SavingGoalResponse;
import com.elias.mismonedas.planning.mapper.SavingGoalMapper;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.exception.CurrencyConversionException;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavingGoalServiceImplTest {

    @Mock private SavingGoalRepository sgRepository;
    @Mock private SavingGoalMapper sgMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CurrencyConverter currencyConverter;
    @Mock private SecurityService securityService;
    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletRepository walletRepository;

    private SavingGoalServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new SavingGoalServiceImpl(
                sgRepository, sgMapper, eventPublisher, currencyConverter,
                securityService, transactionRepository, walletRepository);

        user = new User();
        user.setId(1L);
    }

    @Test
    void shouldDegradeToPartialWhenACurrencyPairHasNoRate() {
        SavingGoal goal = goal(5L, "PEN");
        SavingGoalAggregate row = row(5L, "USD", "PEN", new BigDecimal("10.00"), 1L);

        when(securityService.getCurrentUser()).thenReturn(user);
        when(sgRepository.findByIdAndUser_Id(5L, 1L)).thenReturn(Optional.of(goal));
        when(transactionRepository.sumAmountsByCurrencyForSavingGoals(1L, Set.of(5L)))
                .thenReturn(List.of(row));
        when(currencyConverter.convert(any(Money.class), eq("PEN")))
                .thenThrow(new CurrencyConversionException("sin tipo de cambio"));
        when(sgMapper.toResponse(eq(goal), any(SavingGoalTotals.class)))
                .thenReturn(new SavingGoalResponse());

        service.findById(5L);

        ArgumentCaptor<SavingGoalTotals> totals = ArgumentCaptor.forClass(SavingGoalTotals.class);
        verify(sgMapper).toResponse(eq(goal), totals.capture());
        assertThat(totals.getValue().partial()).isTrue();
        assertThat(totals.getValue().missingRates()).containsExactly("USD->PEN");
        assertThat(totals.getValue().accumulated()).isEqualByComparingTo("0");
    }

    @Test
    void shouldAccumulateConvertedRowsWhenAllRatesExist() {
        SavingGoal goal = goal(5L, "PEN");
        SavingGoalAggregate row = row(5L, "USD", "PEN", new BigDecimal("10.00"), 2L);

        when(securityService.getCurrentUser()).thenReturn(user);
        when(sgRepository.findByIdAndUser_Id(5L, 1L)).thenReturn(Optional.of(goal));
        when(transactionRepository.sumAmountsByCurrencyForSavingGoals(1L, Set.of(5L)))
                .thenReturn(List.of(row));
        when(currencyConverter.convert(any(Money.class), eq("PEN")))
                .thenReturn(Money.of(new BigDecimal("37.50"), "PEN"));
        when(sgMapper.toResponse(eq(goal), any(SavingGoalTotals.class)))
                .thenReturn(new SavingGoalResponse());

        service.findById(5L);

        ArgumentCaptor<SavingGoalTotals> totals = ArgumentCaptor.forClass(SavingGoalTotals.class);
        verify(sgMapper).toResponse(eq(goal), totals.capture());
        assertThat(totals.getValue().partial()).isFalse();
        assertThat(totals.getValue().missingRates()).isEmpty();
        assertThat(totals.getValue().accumulated()).isEqualByComparingTo("37.50");
        assertThat(totals.getValue().transactionsCount()).isEqualTo(2);
    }

    private SavingGoal goal(Long id, String currencyCode) {
        SavingGoal goal = new SavingGoal();
        goal.setId(id);
        goal.setUser(user);
        goal.setCurrencyCode(currencyCode);
        goal.setTargetAmount(new BigDecimal("100.00"));
        return goal;
    }

    private SavingGoalAggregate row(Long goalId, String currency, String goalCurrency, BigDecimal total, Long count) {
        return new SavingGoalAggregate() {
            @Override public Long getGoalId() { return goalId; }
            @Override public String getGoalCurrencyCode() { return goalCurrency; }
            @Override public String getCurrencyCode() { return currency; }
            @Override public BigDecimal getTotal() { return total; }
            @Override public Long getTransactionsCount() { return count; }
        };
    }
}
