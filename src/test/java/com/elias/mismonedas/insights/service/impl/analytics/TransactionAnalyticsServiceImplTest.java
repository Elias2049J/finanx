package com.elias.mismonedas.insights.service.impl.analytics;

import com.elias.mismonedas.catalog.dto.category.CategorySummary;
import com.elias.mismonedas.catalog.dto.reason.ReasonSummary;
import com.elias.mismonedas.catalog.mapper.CategoryMapper;
import com.elias.mismonedas.catalog.mapper.ReasonMapper;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.repository.ReasonRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.insights.dto.analytics.dashboard.TransactionsDashboard;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.mapper.TransactionMapper;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.exception.CurrencyConversionException;
import com.elias.mismonedas.shared.mapper.DateMapper;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransactionAnalyticsServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long WALLET_ID = 42L;
    private static final DateRange RANGE =
            new DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 10, 12, 0);
    private static final String LABEL = "01 jun 2026 - 30 jun 2026";

    private final DateMapper dateMapper = mock(DateMapper.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final ReasonRepository reasonRepository = mock(ReasonRepository.class);
    private final TransactionMapper transactionMapper = mock(TransactionMapper.class);
    private final ReasonMapper reasonMapper = mock(ReasonMapper.class);
    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final CurrencyConverter currencyConverter = mock(CurrencyConverter.class);
    private final SecurityService securityService = mock(SecurityService.class);

    private final TransactionAnalyticsServiceImpl service = new TransactionAnalyticsServiceImpl(
            dateMapper,
            userRepository,
            transactionRepository,
            categoryRepository,
            reasonRepository,
            transactionMapper,
            reasonMapper,
            categoryMapper,
            walletRepository,
            currencyConverter,
            securityService);

    @Test
    void shouldConsolidateAllWalletsInBaseCurrencyWhenWalletIdIsMissing() throws Exception {
        stubUser("PEN");
        when(dateMapper.dateRangeLabel(any(DateRange.class))).thenReturn(LABEL);
        stubGroupingMappers();
        stubConversionAt(3.75);

        Transaction incomePen = stubTransaction(response("800.00", "PEN", TransactionType.INCOME));
        Transaction spentPen = stubTransaction(response("200.00", "PEN", TransactionType.SPENT));
        Transaction spentUsd = stubTransaction(response("100.00", "USD", TransactionType.SPENT));
        when(transactionRepository.findAll(anySpecification()))
                .thenReturn(List.of(incomePen, spentPen, spentUsd));

        TransactionsDashboard dashboard = (TransactionsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getBalance()).isEqualByComparingTo("225.00");
        assertThat(dashboard.getCurrencyCode()).isEqualTo("PEN");
        assertThat(dashboard.isPartial()).isFalse();
        assertThat(dashboard.getMissingRates()).isEmpty();
        assertThat(dashboard.getDateRangeLabel()).isEqualTo(LABEL);
        assertThat(dashboard.getDailyExpenses().getDateRangeLabel()).isEqualTo(LABEL);
        verifyNoInteractions(walletRepository);
    }

    @Test
    void shouldFilterByWalletWhenItBelongsToTheUser() throws Exception {
        stubUser("PEN");
        when(dateMapper.dateRangeLabel(any(DateRange.class))).thenReturn(LABEL);
        stubGroupingMappers();
        stubConversionAt(3.75);
        when(walletRepository.findByIdAndUser_Id(WALLET_ID, USER_ID))
                .thenReturn(Optional.of(mock(Wallet.class)));

        Transaction spentPen = stubTransaction(response("150.00", "PEN", TransactionType.SPENT));
        when(transactionRepository.findAll(anySpecification())).thenReturn(List.of(spentPen));

        TransactionsDashboard dashboard =
                (TransactionsDashboard) service.buildBalanceDashboard(RANGE, USER_ID, WALLET_ID);

        assertThat(dashboard.getBalance()).isEqualByComparingTo("-150.00");
        verify(walletRepository).findByIdAndUser_Id(WALLET_ID, USER_ID);
        verify(transactionRepository).findAll(anySpecification());
    }

    @Test
    void shouldRejectWalletFromAnotherUser() {
        stubUser("PEN");
        when(walletRepository.findByIdAndUser_Id(WALLET_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildBalanceDashboard(RANGE, USER_ID, WALLET_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(String.valueOf(WALLET_ID));

        verify(transactionRepository, never()).findAll(anySpecification());
    }

    @Test
    void shouldKeepRawAmountsWhenUserHasNoPreferences() throws Exception {
        stubUser(null);
        when(dateMapper.dateRangeLabel(any(DateRange.class))).thenReturn(LABEL);
        stubGroupingMappers();

        Transaction incomePen = stubTransaction(response("800.00", "PEN", TransactionType.INCOME));
        Transaction spentUsd = stubTransaction(response("100.00", "USD", TransactionType.SPENT));
        when(transactionRepository.findAll(anySpecification())).thenReturn(List.of(incomePen, spentUsd));

        TransactionsDashboard dashboard = (TransactionsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getCurrencyCode()).isNull();
        assertThat(dashboard.getBalance()).isEqualByComparingTo("700.00");
        verifyNoInteractions(currencyConverter);
    }

    @Test
    void shouldDegradeToPartialWhenRateIsMissing() throws Exception {
        stubUser("PEN");
        when(dateMapper.dateRangeLabel(any(DateRange.class))).thenReturn(LABEL);
        stubGroupingMappers();
        when(currencyConverter.convert(any(Money.class), anyString()))
                .thenThrow(new CurrencyConversionException("sin tasa"));

        Transaction spentPen = stubTransaction(response("200.00", "PEN", TransactionType.SPENT));
        Transaction firstUsd = stubTransaction(response("100.00", "USD", TransactionType.SPENT));
        Transaction secondUsd = stubTransaction(response("50.00", "USD", TransactionType.SPENT));
        when(transactionRepository.findAll(anySpecification()))
                .thenReturn(List.of(spentPen, firstUsd, secondUsd));

        TransactionsDashboard dashboard = (TransactionsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getBalance()).isEqualByComparingTo("-200.00");
        assertThat(dashboard.isPartial()).isTrue();
        assertThat(dashboard.getMissingRates()).containsExactly("USD->PEN");
        verify(currencyConverter, times(1)).convert(any(Money.class), eq("PEN"));
    }

    @Test
    void shouldSplitCategorySharesByTransactionType() throws Exception {
        stubUser("PEN");
        when(dateMapper.dateRangeLabel(any(DateRange.class))).thenReturn(LABEL);
        when(reasonMapper.toReasonSummary(any(TransactionResponse.class)))
                .thenReturn(new ReasonSummary(1L, "General"));
        when(categoryMapper.toSummary(any(TransactionResponse.class))).thenAnswer(invocation -> {
            TransactionResponse t = invocation.getArgument(0);
            return t.getType() == TransactionType.SPENT
                    ? new CategorySummary(2L, "Gasto", TransactionType.SPENT)
                    : new CategorySummary(3L, "Ingreso", TransactionType.INCOME);
        });

        Transaction incomeA = stubTransaction(response("700.00", "PEN", TransactionType.INCOME));
        Transaction incomeB = stubTransaction(response("300.00", "PEN", TransactionType.INCOME));
        Transaction spentA = stubTransaction(response("250.00", "PEN", TransactionType.SPENT));
        when(transactionRepository.findAll(anySpecification()))
                .thenReturn(List.of(incomeA, incomeB, spentA));

        TransactionsDashboard dashboard = (TransactionsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getTopIncomeCategories().getItems()).hasSize(1);
        assertThat(dashboard.getTopIncomeCategories().getItems().get(0)
                .getAggregate().getFactorName()).isEqualTo("Ingreso");
        assertThat(dashboard.getTopIncomeCategories().getItems().get(0)
                .getAggregate().getSharePercentage()).isEqualByComparingTo("100.00");

        assertThat(dashboard.getTopSpentCategories().getItems()).hasSize(1);
        assertThat(dashboard.getTopSpentCategories().getItems().get(0)
                .getAggregate().getFactorName()).isEqualTo("Gasto");
        assertThat(dashboard.getTopSpentCategories().getItems().get(0)
                .getAggregate().getSharePercentage()).isEqualByComparingTo("100.00");
    }

    private void stubUser(String currencyCode) {
        User user = new User();
        if (currencyCode != null) {
            user.setPreferences(UserPreferences.builder()
                    .currencyCode(currencyCode)
                    .timeZone(TimeZone.AMERICA_LIMA)
                    .build());
        }
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void stubGroupingMappers() {
        when(reasonMapper.toReasonSummary(any(TransactionResponse.class)))
                .thenReturn(new ReasonSummary(1L, "General"));
        when(categoryMapper.toSummary(any(TransactionResponse.class)))
                .thenReturn(new CategorySummary(1L, "General", TransactionType.SPENT));
    }

    private void stubConversionAt(double rate) {
        when(currencyConverter.convert(any(Money.class), anyString())).thenAnswer(invocation -> {
            Money money = invocation.getArgument(0);
            String target = invocation.getArgument(1);
            BigDecimal converted = money.amount()
                    .multiply(BigDecimal.valueOf(rate))
                    .setScale(2, RoundingMode.HALF_UP);
            return new Money(converted, target);
        });
    }

    private Transaction stubTransaction(TransactionResponse response) {
        Transaction entity = mock(Transaction.class);
        when(transactionMapper.toResponse(entity)).thenReturn(response);
        return entity;
    }

    private TransactionResponse response(String amount, String currency, TransactionType type) {
        TransactionResponse response = new TransactionResponse();
        response.setAmount(new BigDecimal(amount));
        response.setWalletCurrency(currency);
        response.setType(type);
        response.setPaymentMethod(PaymentMethod.CASH);
        response.setCreatedAt(CREATED_AT);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Specification<Transaction> anySpecification() {
        return any(Specification.class);
    }
}
