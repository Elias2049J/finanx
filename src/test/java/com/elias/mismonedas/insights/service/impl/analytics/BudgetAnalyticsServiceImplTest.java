package com.elias.mismonedas.insights.service.impl.analytics;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.insights.dto.analytics.dashboard.BudgetsDashboard;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.mapper.BudgetMapper;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.exception.CurrencyConversionException;
import com.elias.mismonedas.shared.mapper.DateMapper;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BudgetAnalyticsServiceImplTest {

    private static final Long USER_ID = 9L;
    private static final Long CATEGORY_ID = 10L;
    private static final DateRange RANGE =
            new DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
    private static final OffsetDateTime START = OffsetDateTime.now().minusDays(5);
    private static final OffsetDateTime END = OffsetDateTime.now().plusDays(25);

    private final DateMapper dateMapper = mock(DateMapper.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final BudgetMapper budgetMapper = mock(BudgetMapper.class);
    private final CurrencyConverter currencyConverter = mock(CurrencyConverter.class);
    private final SecurityService securityService = mock(SecurityService.class);

    private final BudgetAnalyticsServiceImpl service = new BudgetAnalyticsServiceImpl(
            dateMapper,
            userRepository,
            budgetRepository,
            transactionRepository,
            budgetMapper,
            currencyConverter,
            securityService);

    @Test
    void shouldConsolidateBudgetsInBaseCurrency() throws Exception {
        stubUser("PEN");
        when(dateMapper.dateRangeLabel(any(DateRange.class))).thenReturn("jun 2026");
        Budget budget = stubBudget("PEN", "500.00");
        stubTransactions(transaction("200.00", null));

        BudgetsDashboard dashboard = (BudgetsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getCurrencyCode()).isEqualTo("PEN");
        assertThat(dashboard.getTotalBudgetLimitAmount()).isEqualByComparingTo("500.00");
        assertThat(dashboard.getTotalBudgetSpentAmount()).isEqualByComparingTo("200.00");
        assertThat(dashboard.getTotalBudgetRemainingAmount()).isEqualByComparingTo("300.00");
        assertThat(dashboard.isPartial()).isFalse();
        assertThat(dashboard.getMissingRates()).isEmpty();
        verifyNoInteractions(currencyConverter);
    }

    @Test
    void shouldConvertForeignTransactionsUsingCurrentRate() throws Exception {
        stubUser("PEN");
        when(dateMapper.dateRangeLabel(any(DateRange.class))).thenReturn("jun 2026");
        stubBudget("PEN", "500.00");
        stubTransactions(transaction("100.00", "USD"));
        when(currencyConverter.convert(any(Money.class), eq("PEN")))
                .thenReturn(new Money(new BigDecimal("375.00"), "PEN"));

        BudgetsDashboard dashboard = (BudgetsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getTotalBudgetSpentAmount()).isEqualByComparingTo("375.00");
        assertThat(dashboard.isPartial()).isFalse();
        assertThat(dashboard.getMissingRates()).isEmpty();
    }

    @Test
    void shouldDegradeWhenBudgetCurrencyHasNoRate() throws Exception {
        stubUser("PEN");
        when(dateMapper.dateRangeLabel(any(DateRange.class))).thenReturn("jun 2026");
        stubBudget("USD", "100.00");
        stubTransactions(transaction("100.00", "USD"));
        when(currencyConverter.convert(any(Money.class), eq("PEN")))
                .thenThrow(new CurrencyConversionException("sin tasa"));

        BudgetsDashboard dashboard = (BudgetsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getTotalBudgetLimitAmount()).isEqualByComparingTo("0");
        assertThat(dashboard.getTotalBudgetSpentAmount()).isEqualByComparingTo("0");
        assertThat(dashboard.isPartial()).isTrue();
        assertThat(dashboard.getMissingRates()).containsExactly("USD->PEN");
        verify(currencyConverter, times(1)).convert(any(Money.class), eq("PEN"));
    }

    @Test
    void shouldExcludeOnlyTransactionsWithoutRate() throws Exception {
        stubUser("PEN");
        when(dateMapper.dateRangeLabel(any(DateRange.class))).thenReturn("jun 2026");
        stubBudget("PEN", "500.00");
        stubTransactions(transaction("200.00", null), transaction("50.00", "USD"));
        when(currencyConverter.convert(any(Money.class), eq("PEN")))
                .thenThrow(new CurrencyConversionException("sin tasa"));

        BudgetsDashboard dashboard = (BudgetsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getTotalBudgetLimitAmount()).isEqualByComparingTo("500.00");
        assertThat(dashboard.getTotalBudgetSpentAmount()).isEqualByComparingTo("200.00");
        assertThat(dashboard.getTotalBudgetRemainingAmount()).isEqualByComparingTo("300.00");
        assertThat(dashboard.isPartial()).isTrue();
        assertThat(dashboard.getMissingRates()).containsExactly("USD->PEN");
    }

    @Test
    void shouldKeepRawAmountsWhenUserHasNoPreferences() throws Exception {
        stubUser(null);
        when(dateMapper.dateRangeLabel(any(DateRange.class))).thenReturn("jun 2026");
        stubBudget("PEN", "500.00");
        stubTransactions(transaction("200.00", null));

        BudgetsDashboard dashboard = (BudgetsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getCurrencyCode()).isNull();
        assertThat(dashboard.getTotalBudgetLimitAmount()).isEqualByComparingTo("500.00");
        assertThat(dashboard.getTotalBudgetSpentAmount()).isEqualByComparingTo("200.00");
        assertThat(dashboard.isPartial()).isFalse();
        verifyNoInteractions(currencyConverter);
    }

    @Test
    void shouldKeepStrictConversionForSingleExecution() {
        stubUser("PEN");
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(CATEGORY_ID);
        when(category.getName()).thenReturn("General");
        Budget budget = mock(Budget.class);
        when(budget.getId()).thenReturn(1L);
        when(budget.getCategory()).thenReturn(category);
        when(budget.getCurrencyCode()).thenReturn("USD");
        when(budget.getLimitAmount()).thenReturn(new BigDecimal("100.00"));
        when(budget.getStart()).thenReturn(START);
        when(budget.getEnd()).thenReturn(END);
        when(budget.getUser()).thenReturn(userWithPreferences("PEN"));
        when(budgetRepository.findByIdAndUser_Id(1L, USER_ID)).thenReturn(Optional.of(budget));
        when(securityService.getCurrentUser()).thenReturn(userWithPreferences("PEN"));
        stubTransactions(transaction("100.00", "PEN"));
        when(currencyConverter.convert(any(Money.class), eq("USD")))
                .thenThrow(new CurrencyConversionException("sin tasa"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getBudgetExecution(1L))
                .isInstanceOf(CurrencyConversionException.class);
        verify(currencyConverter, never()).convert(any(Money.class), eq("PEN"));
    }

    private void stubUser(String currencyCode) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithPreferences(currencyCode)));
    }

    private User userWithPreferences(String currencyCode) {
        User user = new User();
        user.setId(USER_ID);
        if (currencyCode != null) {
            user.setPreferences(UserPreferences.builder()
                    .currencyCode(currencyCode)
                    .timeZone(TimeZone.AMERICA_LIMA)
                    .build());
        }
        return user;
    }

    private Budget stubBudget(String currencyCode, String limitAmount) {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(CATEGORY_ID);

        Budget budget = mock(Budget.class);
        when(budget.getCategory()).thenReturn(category);
        when(budget.getCurrencyCode()).thenReturn(currencyCode);
        when(budget.getLimitAmount()).thenReturn(new BigDecimal(limitAmount));
        when(budget.getStart()).thenReturn(START);
        when(budget.getEnd()).thenReturn(END);
        when(budgetRepository.findAll(anySpecification())).thenReturn(List.of(budget));
        return budget;
    }

    private void stubTransactions(Transaction... transactions) {
        when(transactionRepository.findAllByCategory_IdAndCreatedAtBetweenAndActive(
                eq(CATEGORY_ID), any(OffsetDateTime.class), any(OffsetDateTime.class), eq(true)))
                .thenReturn(List.of(transactions));
    }

    private Transaction transaction(String amount, String walletCurrency) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getAmount()).thenReturn(new BigDecimal(amount));
        if (walletCurrency != null) {
            Wallet wallet = mock(Wallet.class);
            when(wallet.getCurrencyCode()).thenReturn(walletCurrency);
            when(transaction.getWallet()).thenReturn(wallet);
        }
        return transaction;
    }

    @SuppressWarnings("unchecked")
    private Specification<Budget> anySpecification() {
        return any(Specification.class);
    }
}
