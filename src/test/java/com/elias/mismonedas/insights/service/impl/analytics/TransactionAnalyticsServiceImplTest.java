package com.elias.mismonedas.insights.service.impl.analytics;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.insights.dto.analytics.dashboard.TransactionsDashboard;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.repository.projection.AnalyticsDailyAggregate;
import com.elias.mismonedas.ledger.repository.projection.AnalyticsDimensionAggregate;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.exception.CurrencyConversionException;
import com.elias.mismonedas.shared.mapper.DateMapper;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
    private static final String LABEL = "01 jun 2026 - 30 jun 2026";
    private static final String LIMA = "America/Lima";

    private final DateMapper dateMapper = mock(DateMapper.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final CurrencyConverter currencyConverter = mock(CurrencyConverter.class);
    private final SecurityService securityService = mock(SecurityService.class);

    private final TransactionAnalyticsServiceImpl service = new TransactionAnalyticsServiceImpl(
            dateMapper,
            userRepository,
            transactionRepository,
            walletRepository,
            currencyConverter,
            securityService);

    @BeforeEach
    void setUp() {
        when(dateMapper.dateRangeLabel(any(DateRange.class))).thenReturn(LABEL);
    }

    @Test
    void shouldConsolidateAllWalletsInBaseCurrencyWhenWalletIdIsMissing() {
        stubUser("PEN");
        stubConversionAt(3.75);

        stubAggregates(
                List.of(
                        dimension("PEN", TransactionType.INCOME, 3L, "Sueldo", 1L, "General",
                                "800.00", 1, "800.00", "800.00"),
                        dimension("PEN", TransactionType.SPENT, 2L, "Comida", 1L, "General",
                                "200.00", 1, "200.00", "200.00"),
                        dimension("USD", TransactionType.SPENT, 2L, "Comida", 1L, "General",
                                "100.00", 1, "100.00", "100.00")),
                List.of(
                        daily(LocalDate.of(2026, 6, 10), TransactionType.INCOME, "PEN", "800.00", 1),
                        daily(LocalDate.of(2026, 6, 10), TransactionType.SPENT, "PEN", "200.00", 1),
                        daily(LocalDate.of(2026, 6, 10), TransactionType.SPENT, "USD", "100.00", 1)));

        TransactionsDashboard dashboard = (TransactionsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getBalance()).isEqualByComparingTo("225.00");
        assertThat(dashboard.getCurrencyCode()).isEqualTo("PEN");
        assertThat(dashboard.isPartial()).isFalse();
        assertThat(dashboard.getMissingRates()).isEmpty();
        assertThat(dashboard.getDateRangeLabel()).isEqualTo(LABEL);
        assertThat(dashboard.getDailyExpenses().getDateRangeLabel()).isEqualTo(LABEL);

        assertThat(dashboard.getAggregate().getTotalAmount()).isEqualByComparingTo("1375.00");
        assertThat(dashboard.getAggregate().getOccurrenceCount()).isEqualTo(3);
        assertThat(dashboard.getAggregate().getAverageAmount()).isEqualByComparingTo("458.33");
        assertThat(dashboard.getAggregate().getMin()).isEqualByComparingTo("200.00");
        assertThat(dashboard.getAggregate().getMax()).isEqualByComparingTo("800.00");

        assertThat(dashboard.getDailyExpenses().getPoints()).hasSize(1);
        assertThat(dashboard.getDailyExpenses().getPoints().get(0).getAmount())
                .isEqualByComparingTo("575.00");
        assertThat(dashboard.getDailyExpenses().getPoints().get(0).getOccurrenceCount())
                .isEqualTo(2);
        assertThat(dashboard.getDailyIncome().getPoints()).hasSize(1);
        assertThat(dashboard.getDailyIncome().getPoints().get(0).getAmount())
                .isEqualByComparingTo("800.00");

        assertThat(dashboard.getTopSpentCategories().getItems()).hasSize(1);
        assertThat(dashboard.getTopSpentCategories().getItems().get(0)
                .getAggregate().getFactorName()).isEqualTo("Comida");
        assertThat(dashboard.getTopSpentCategories().getItems().get(0)
                .getAggregate().getSharePercentage()).isEqualByComparingTo("100.00");
        assertThat(dashboard.getTopIncomeCategories().getItems().get(0)
                .getAggregate().getFactorName()).isEqualTo("Sueldo");

        assertThat(dashboard.getSummaryByType().getCount()).isEqualTo(2);
        assertThat(dashboard.getSummaryByPaymentMethod().getCount()).isEqualTo(1);
        verify(transactionRepository, never()).findAll(any(Specification.class));
    }

    @Test
    void shouldFilterByWalletWhenItBelongsToTheUser() {
        stubUser("PEN");
        when(walletRepository.findByIdAndUser_Id(WALLET_ID, USER_ID))
                .thenReturn(Optional.of(mock(Wallet.class)));
        List<AnalyticsDimensionAggregate> dimensions = List.of(
                dimension("PEN", TransactionType.SPENT, "150.00", 1, "150.00", "150.00"));
        List<AnalyticsDailyAggregate> daily = List.of(
                daily(LocalDate.of(2026, 6, 10), TransactionType.SPENT, "PEN", "150.00", 1));
        when(transactionRepository.aggregateByDimensions(eq(USER_ID), eq(WALLET_ID), any(), any()))
                .thenReturn(dimensions);
        when(transactionRepository.aggregateDaily(
                eq(USER_ID), eq(WALLET_ID), any(), any(), eq(LIMA)))
                .thenReturn(daily);

        TransactionsDashboard dashboard =
                (TransactionsDashboard) service.buildBalanceDashboard(RANGE, USER_ID, WALLET_ID);

        assertThat(dashboard.getBalance()).isEqualByComparingTo("-150.00");
        verify(walletRepository).findByIdAndUser_Id(WALLET_ID, USER_ID);
        verify(transactionRepository).aggregateByDimensions(eq(USER_ID), eq(WALLET_ID), any(), any());
        verify(transactionRepository).aggregateDaily(
                eq(USER_ID), eq(WALLET_ID), any(), any(), eq(LIMA));
        verifyNoInteractions(currencyConverter);
    }

    @Test
    void shouldRejectWalletFromAnotherUser() {
        stubUser("PEN");
        when(walletRepository.findByIdAndUser_Id(WALLET_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildBalanceDashboard(RANGE, USER_ID, WALLET_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(String.valueOf(WALLET_ID));

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void shouldKeepRawAmountsWhenUserHasNoPreferences() {
        stubUser(null);

        stubAggregates(
                List.of(
                        dimension("PEN", TransactionType.INCOME, "800.00", 1, "800.00", "800.00"),
                        dimension("USD", TransactionType.SPENT, "100.00", 1, "100.00", "100.00")),
                List.of(
                        daily(LocalDate.of(2026, 6, 10), TransactionType.INCOME, "PEN", "800.00", 1),
                        daily(LocalDate.of(2026, 6, 10), TransactionType.SPENT, "USD", "100.00", 1)));

        TransactionsDashboard dashboard = (TransactionsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getCurrencyCode()).isNull();
        assertThat(dashboard.getBalance()).isEqualByComparingTo("700.00");
        assertThat(dashboard.isPartial()).isFalse();
        verifyNoInteractions(currencyConverter);
    }

    @Test
    void shouldDegradeToPartialWhenRateIsMissing() {
        stubUser("PEN");
        when(currencyConverter.convert(any(Money.class), anyString()))
                .thenThrow(new CurrencyConversionException("sin tasa"));

        stubAggregates(
                List.of(
                        dimension("PEN", TransactionType.SPENT, "200.00", 1, "200.00", "200.00"),
                        dimension("USD", TransactionType.SPENT, "100.00", 1, "100.00", "100.00")),
                List.of(
                        daily(LocalDate.of(2026, 6, 10), TransactionType.SPENT, "PEN", "200.00", 1),
                        daily(LocalDate.of(2026, 6, 10), TransactionType.SPENT, "USD", "100.00", 1)));

        TransactionsDashboard dashboard = (TransactionsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getBalance()).isEqualByComparingTo("-200.00");
        assertThat(dashboard.isPartial()).isTrue();
        assertThat(dashboard.getMissingRates()).containsExactly("USD->PEN");
        assertThat(dashboard.getDailyExpenses().getAggregate().getTotalAmount())
                .isEqualByComparingTo("200.00");
        verify(currencyConverter, times(1)).convert(any(Money.class), eq("PEN"));
    }

    @Test
    void shouldSplitCategorySharesByTransactionType() {
        stubUser("PEN");

        stubAggregates(
                List.of(
                        dimension("PEN", TransactionType.INCOME, 3L, "Ingreso", 1L, "General",
                                "1000.00", 2, "300.00", "700.00"),
                        dimension("PEN", TransactionType.SPENT, 2L, "Gasto", 1L, "General",
                                "250.00", 1, "250.00", "250.00")),
                List.of(
                        daily(LocalDate.of(2026, 6, 10), TransactionType.INCOME, "PEN", "1000.00", 2),
                        daily(LocalDate.of(2026, 6, 10), TransactionType.SPENT, "PEN", "250.00", 1)));

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
        assertThat(dashboard.getSummaryByCategory().getCount()).isEqualTo(2);
    }

    @Test
    void shouldBuildDailyTimelineFromAggregatesAndCountActiveDays() {
        stubUser("PEN");

        stubAggregates(
                List.of(dimension("PEN", TransactionType.SPENT, "80.00", 3, "20.00", "30.00")),
                List.of(
                        daily(LocalDate.of(2026, 6, 10), TransactionType.SPENT, "PEN", "50.00", 2),
                        daily(LocalDate.of(2026, 6, 11), TransactionType.SPENT, "PEN", "30.00", 1)));

        TransactionsDashboard dashboard = (TransactionsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getDailyExpenses().getPoints()).hasSize(2);
        assertThat(dashboard.getDailyExpenses().getPoints().get(0).getDate())
                .isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(dashboard.getDailyExpenses().getPoints().get(0).getAmount())
                .isEqualByComparingTo("50.00");
        assertThat(dashboard.getDailyExpenses().getPoints().get(0).getOccurrenceCount())
                .isEqualTo(2);
        assertThat(dashboard.getDailyExpenses().getPoints().get(1).getAmount())
                .isEqualByComparingTo("30.00");

        assertThat(dashboard.getDailyExpenses().getAggregate().getTotalAmount())
                .isEqualByComparingTo("80.00");
        assertThat(dashboard.getDailyExpenses().getAggregate().getAverageAmount())
                .isEqualByComparingTo("40.00");
        assertThat(dashboard.getDailyExpenses().getAggregate().getOccurrenceCount())
                .isEqualTo(2);
        assertThat(dashboard.getDailyExpenses().getAggregate().getMin())
                .isEqualByComparingTo("30.00");
        assertThat(dashboard.getDailyExpenses().getAggregate().getMax())
                .isEqualByComparingTo("50.00");
        assertThat(dashboard.getDailyExpenses().getCount()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyDashboardWhenThereAreNoTransactions() {
        stubUser("PEN");

        stubAggregates(List.of(), List.of());

        TransactionsDashboard dashboard = (TransactionsDashboard) service.buildDashboard(RANGE, USER_ID);

        assertThat(dashboard.getBalance()).isEqualByComparingTo("0");
        assertThat(dashboard.getAggregate().getTotalAmount()).isEqualByComparingTo("0");
        assertThat(dashboard.getAggregate().getOccurrenceCount()).isZero();
        assertThat(dashboard.getAggregate().getAverageAmount()).isEqualByComparingTo("0");
        assertThat(dashboard.getDailyExpenses().getPoints()).isEmpty();
        assertThat(dashboard.getDailyExpenses().getCount()).isZero();
        assertThat(dashboard.getTopSpentCategories().getItems()).isEmpty();
        assertThat(dashboard.getSummaryByType().getCount()).isZero();
        verifyNoInteractions(currencyConverter);
    }

    private void stubUser(String currencyCode) {
        User user = new User();
        user.setId(USER_ID);
        if (currencyCode != null) {
            user.setPreferences(UserPreferences.builder()
                    .timeZone(TimeZone.AMERICA_LIMA)
                    .build());
        }
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        if (currencyCode != null) {
            Wallet primary = mock(Wallet.class);
            when(primary.getCurrencyCode()).thenReturn(currencyCode);
            when(walletRepository.findByUserIdAndPrimaryTrue(USER_ID)).thenReturn(Optional.of(primary));
        } else {
            when(walletRepository.findByUserIdAndPrimaryTrue(USER_ID)).thenReturn(Optional.empty());
        }
    }

    private void stubAggregates(List<AnalyticsDimensionAggregate> dimensions,
                                List<AnalyticsDailyAggregate> daily) {
        when(transactionRepository.aggregateByDimensions(eq(USER_ID), any(), any(), any()))
                .thenReturn(dimensions);
        when(transactionRepository.aggregateDaily(eq(USER_ID), any(), any(), any(), eq(LIMA)))
                .thenReturn(daily);
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

    private AnalyticsDimensionAggregate dimension(String currency,
                                                  TransactionType type,
                                                  String total,
                                                  long count,
                                                  String min,
                                                  String max) {
        return dimension(currency, type, 2L, "Comida", 1L, "General", total, count, min, max);
    }

    private AnalyticsDimensionAggregate dimension(String currency,
                                                  TransactionType type,
                                                  Long categoryId,
                                                  String categoryName,
                                                  Long reasonId,
                                                  String reasonDescription,
                                                  String total,
                                                  long count,
                                                  String min,
                                                  String max) {
        AnalyticsDimensionAggregate row = mock(AnalyticsDimensionAggregate.class);
        when(row.getCurrencyCode()).thenReturn(currency);
        when(row.getType()).thenReturn(type);
        when(row.getPaymentMethod()).thenReturn(PaymentMethod.CASH);
        when(row.getCategoryId()).thenReturn(categoryId);
        when(row.getCategoryName()).thenReturn(categoryName);
        when(row.getReasonId()).thenReturn(reasonId);
        when(row.getReasonDescription()).thenReturn(reasonDescription);
        when(row.getTotal()).thenReturn(new BigDecimal(total));
        when(row.getOccurrences()).thenReturn(count);
        when(row.getMinAmount()).thenReturn(new BigDecimal(min));
        when(row.getMaxAmount()).thenReturn(new BigDecimal(max));
        return row;
    }

    private AnalyticsDailyAggregate daily(LocalDate day,
                                          TransactionType type,
                                          String currency,
                                          String total,
                                          long count) {
        AnalyticsDailyAggregate row = mock(AnalyticsDailyAggregate.class);
        when(row.getDay()).thenReturn(day);
        when(row.getType()).thenReturn(type);
        when(row.getCurrencyCode()).thenReturn(currency);
        when(row.getTotal()).thenReturn(new BigDecimal(total));
        when(row.getOccurrences()).thenReturn(count);
        return row;
    }
}
