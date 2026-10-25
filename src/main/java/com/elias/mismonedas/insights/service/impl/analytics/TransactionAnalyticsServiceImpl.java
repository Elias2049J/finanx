package com.elias.mismonedas.insights.service.impl.analytics;

import com.elias.mismonedas.catalog.dto.category.CategorySummary;
import com.elias.mismonedas.catalog.dto.reason.ReasonSummary;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.insights.dto.analytics.component.*;
import com.elias.mismonedas.insights.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.insights.dto.analytics.dashboard.TransactionsDashboard;
import com.elias.mismonedas.insights.service.analytics.TransactionAnalyticsService;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.repository.projection.AnalyticsDailyAggregate;
import com.elias.mismonedas.ledger.repository.projection.AnalyticsDimensionAggregate;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.exception.CurrencyConversionException;
import com.elias.mismonedas.shared.mapper.DateMapper;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service("transactionAnalyticsService")
public class TransactionAnalyticsServiceImpl extends AnalyticsServiceImpl implements TransactionAnalyticsService {
    protected final TransactionRepository transactionRepository;
    protected final CurrencyConverter currencyConverter;

    public TransactionAnalyticsServiceImpl(
            DateMapper dateMapper,
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            WalletRepository walletRepository,
            CurrencyConverter currencyConverter,
            SecurityService securityService) {
        super(dateMapper, userRepository, securityService, walletRepository);
        this.transactionRepository = transactionRepository;
        this.currencyConverter = currencyConverter;
    }

    @Override
    public DashboardResponse buildDashboard(DateRange request, Long userId) {
        return buildBalanceDashboard(request, userId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse buildBalanceDashboard(DateRange period, Long walletId) {
        return buildBalanceDashboard(period, securityService.getCurrentUser().getId(), walletId);
    }

    @Override
    public DashboardResponse buildBalanceDashboard(DateRange request, Long userId, Long walletId) {
        TransactionsDashboard td = new TransactionsDashboard();

        int topN = 5;
        String title = "Dashboard de Movimientos: " +
                "Gastos e Ingresos Diarios, " +
                "Ranking de Categorías y Motivos, y" +
                " Resúmenes por Categoría, Método de Pago y Tipo";

        User u = this.getUser(userId);
        ZoneId zoneId = u.getTimeZone().toZoneId();
        DateRange coveredPeriod = request.coveringMonths();
        OffsetDateTime periodStart = coveredPeriod.startInclusive(zoneId);
        OffsetDateTime periodEnd = coveredPeriod.endExclusive(zoneId);
        LocalDateTime createdAt = OffsetDateTime.now().atZoneSameInstant(zoneId).toLocalDateTime();
        String baseCurrency = resolveBaseCurrency(u);

        if (walletId != null) {
            walletRepository.findByIdAndUser_Id(walletId, userId)
                    .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + walletId));
        }

        List<String> missingRates = new ArrayList<>();
        Set<String> failedCurrencies = new HashSet<>();

        List<ConvertedGroup> groups = transactionRepository.aggregateByDimensions(
                        userId, walletId, periodStart, periodEnd)
                .stream()
                .map(row -> convertGroup(row, baseCurrency, failedCurrencies, missingRates))
                .filter(Objects::nonNull)
                .toList();

        List<ConvertedDaily> daily = transactionRepository.aggregateDaily(
                        userId, walletId, periodStart, periodEnd, zoneId.getId())
                .stream()
                .map(row -> convertDaily(row, baseCurrency, failedCurrencies, missingRates))
                .filter(Objects::nonNull)
                .toList();

        String dateRangeLabel = dateMapper.dateRangeLabel(coveredPeriod);
        List<String> uniqueMissingRates = missingRates.stream().distinct().sorted().toList();
        td.setDateRangeLabel(dateRangeLabel);
        td.setCurrencyCode(baseCurrency);
        td.setPartial(!uniqueMissingRates.isEmpty());
        td.setMissingRates(uniqueMissingRates);

        BigDecimal totalGeneral = groups.stream()
                .map(ConvertedGroup::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIncome = groups.stream()
                .filter(group -> group.type() == TransactionType.INCOME)
                .map(ConvertedGroup::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpent = groups.stream()
                .filter(group -> group.type() == TransactionType.SPENT)
                .map(ConvertedGroup::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = totalIncome.subtract(totalSpent);

        TimeLine<TransactionResponse> expenses = this.buildTimeLine(
                coveredPeriod,
                "Gastos del periodo :" + dateRangeLabel,
                dailyAmounts(daily, TransactionType.SPENT));

        TimeLine<TransactionResponse> incomes = this.buildTimeLine(
                coveredPeriod,
                "Ingresos del periodo :" + dateRangeLabel,
                dailyAmounts(daily, TransactionType.INCOME));

        List<AggregateBy<ReasonSummary>> spentByReason =
                aggregateByReason(groups, TransactionType.SPENT, totalSpent);
        List<AggregateBy<ReasonSummary>> incomeByReason =
                aggregateByReason(groups, TransactionType.INCOME, totalIncome);

        Ranking<ReasonSummary> rsRanking = this.buildRanking(
                "Top " + topN + " motivos de gastos",
                spentByReason,
                agg -> agg.getAggregate().getTotalAmount(),
                topN
        );

        Ranking<ReasonSummary> riRanking = this.buildRanking(
                "Top " + topN + " fuentes de ingresos",
                incomeByReason,
                agg -> agg.getAggregate().getTotalAmount(),
                topN
        );

        List<AggregateBy<PaymentMethod>> aggsByPaymentMethod = groups.stream()
                .collect(Collectors.groupingBy(ConvertedGroup::paymentMethod))
                .entrySet().stream()
                .map(entry -> aggregateBy(
                        entry.getKey(),
                        entry.getKey().getDisplayName(),
                        entry.getValue(),
                        totalGeneral))
                .toList();

        List<AggregateBy<CategorySummary>> aggsByCategory =
                aggregateByCategory(groups, null, totalGeneral);
        List<AggregateBy<CategorySummary>> spentByCategory =
                aggregateByCategory(groups, TransactionType.SPENT, totalSpent);
        List<AggregateBy<CategorySummary>> incomeByCategory =
                aggregateByCategory(groups, TransactionType.INCOME, totalIncome);

        List<AggregateBy<TransactionType>> aggsByType = groups.stream()
                .collect(Collectors.groupingBy(ConvertedGroup::type))
                .entrySet().stream()
                .map(entry -> aggregateBy(
                        entry.getKey(),
                        entry.getKey().getDisplayName(),
                        entry.getValue(),
                        totalGeneral))
                .toList();

        Ranking<CategorySummary> summaryByCategory = this.buildRanking(
                "Resumen de movimientos por categoría, ordenado por montos",
                aggsByCategory,
                agg -> agg.getAggregate().getTotalAmount(),
                aggsByCategory.size()
        );

        Ranking<CategorySummary> topSpentCategories = this.buildRanking(
                "Top " + topN + " categorías de gasto",
                spentByCategory,
                agg -> agg.getAggregate().getTotalAmount(),
                topN
        );

        Ranking<CategorySummary> topIncomeCategories = this.buildRanking(
                "Top " + topN + " categorías de ingreso",
                incomeByCategory,
                agg -> agg.getAggregate().getTotalAmount(),
                topN
        );

        Ranking<PaymentMethod> summaryByPaymentMethod = this.buildRanking(
                "Resumen de movimientos por método de pago, ordenado por monto",
                aggsByPaymentMethod,
                agg -> agg.getAggregate().getTotalAmount(),
                aggsByPaymentMethod.size()
        );

        Ranking<TransactionType> summaryByType = this.buildRanking(
                "Resumen de movimientos por tipo, ordenado por monto",
                aggsByType,
                agg -> agg.getAggregate().getTotalAmount(),
                aggsByType.size()
        );

        Aggregate generalAggregate = aggregate(
                "Resumen general de movimientos del periodo: " + dateRangeLabel,
                groups
        );

        td.setTitle(title);
        td.setCreatedAt(createdAt);
        td.setBalance(balance);
        td.setDailyExpenses(expenses);
        td.setDailyIncome(incomes);
        td.setTopSpentReasons(rsRanking);
        td.setTopIncomeReasons(riRanking);
        td.setSummaryByCategory(summaryByCategory);
        td.setTopSpentCategories(topSpentCategories);
        td.setTopIncomeCategories(topIncomeCategories);
        td.setSummaryByType(summaryByType);
        td.setSummaryByPaymentMethod(summaryByPaymentMethod);
        td.setAggregate(generalAggregate);
        td.setInsightSummary(this.generateDefaultInsightSummary(td, 2));

        return td;
    }

    private ConvertedGroup convertGroup(AnalyticsDimensionAggregate row,
                                        String baseCurrency,
                                        Set<String> failedCurrencies,
                                        List<String> missingRates) {
        String sourceCurrency = row.getCurrencyCode();

        if (baseCurrency == null || sourceCurrency == null || sourceCurrency.equals(baseCurrency)) {
            return new ConvertedGroup(
                    row.getType(),
                    row.getPaymentMethod(),
                    row.getCategoryId(),
                    row.getCategoryName(),
                    row.getReasonId(),
                    row.getReasonDescription(),
                    row.getTotal(),
                    row.getOccurrences(),
                    row.getMinAmount(),
                    row.getMaxAmount());
        }

        if (failedCurrencies.contains(sourceCurrency)) {
            missingRates.add(sourceCurrency + "->" + baseCurrency);
            return null;
        }

        try {
            return new ConvertedGroup(
                    row.getType(),
                    row.getPaymentMethod(),
                    row.getCategoryId(),
                    row.getCategoryName(),
                    row.getReasonId(),
                    row.getReasonDescription(),
                    convertAmount(row.getTotal(), sourceCurrency, baseCurrency),
                    row.getOccurrences(),
                    convertAmount(row.getMinAmount(), sourceCurrency, baseCurrency),
                    convertAmount(row.getMaxAmount(), sourceCurrency, baseCurrency));
        } catch (CurrencyConversionException ex) {
            failedCurrencies.add(sourceCurrency);
            missingRates.add(sourceCurrency + "->" + baseCurrency);
            log.warn("Sin tasa {}->{}: se excluye el grupo del consolidado", sourceCurrency, baseCurrency);
            return null;
        }
    }

    private ConvertedDaily convertDaily(AnalyticsDailyAggregate row,
                                        String baseCurrency,
                                        Set<String> failedCurrencies,
                                        List<String> missingRates) {
        String sourceCurrency = row.getCurrencyCode();

        if (baseCurrency == null || sourceCurrency == null || sourceCurrency.equals(baseCurrency)) {
            return new ConvertedDaily(
                    row.getDay(),
                    row.getType(),
                    row.getTotal(),
                    row.getOccurrences());
        }

        if (failedCurrencies.contains(sourceCurrency)) {
            missingRates.add(sourceCurrency + "->" + baseCurrency);
            return null;
        }

        try {
            return new ConvertedDaily(
                    row.getDay(),
                    row.getType(),
                    convertAmount(row.getTotal(), sourceCurrency, baseCurrency),
                    row.getOccurrences());
        } catch (CurrencyConversionException ex) {
            failedCurrencies.add(sourceCurrency);
            missingRates.add(sourceCurrency + "->" + baseCurrency);
            log.warn("Sin tasa {}->{}: se excluye el dia del consolidado", sourceCurrency, baseCurrency);
            return null;
        }
    }

    private BigDecimal convertAmount(BigDecimal amount, String sourceCurrency, String baseCurrency) {
        return currencyConverter.convert(Money.of(amount, sourceCurrency), baseCurrency).amount();
    }

    private List<DailyAmount> dailyAmounts(List<ConvertedDaily> rows, TransactionType type) {
        return rows.stream()
                .filter(row -> row.type() == type)
                .collect(Collectors.groupingBy(ConvertedDaily::day))
                .entrySet().stream()
                .map(entry -> new DailyAmount(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(ConvertedDaily::amount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        entry.getValue().stream()
                                .mapToLong(ConvertedDaily::count)
                                .sum()))
                .toList();
    }

    private List<AggregateBy<ReasonSummary>> aggregateByReason(List<ConvertedGroup> groups,
                                                              TransactionType type,
                                                              BigDecimal grandTotal) {
        return groups.stream()
                .filter(group -> group.type() == type)
                .collect(Collectors.groupingBy(
                        group -> new ReasonSummary(group.reasonId(), group.reasonDescription())))
                .entrySet().stream()
                .map(entry -> aggregateBy(
                        entry.getKey(),
                        entry.getKey().getDescription(),
                        entry.getValue(),
                        grandTotal))
                .toList();
    }

    private List<AggregateBy<CategorySummary>> aggregateByCategory(List<ConvertedGroup> groups,
                                                                  TransactionType type,
                                                                  BigDecimal grandTotal) {
        return groups.stream()
                .filter(group -> type == null || group.type() == type)
                .collect(Collectors.groupingBy(
                        group -> new CategorySummary(group.categoryId(), group.categoryName(), group.type())))
                .entrySet().stream()
                .map(entry -> aggregateBy(
                        entry.getKey(),
                        entry.getKey().getName(),
                        entry.getValue(),
                        grandTotal))
                .toList();
    }

    private <F> AggregateBy<F> aggregateBy(F factor,
                                           String factorName,
                                           List<ConvertedGroup> groups,
                                           BigDecimal grandTotal) {
        GroupTotals totals = totals(groups);
        return buildAggregateBy(
                factor,
                factorName,
                totals.total(),
                totals.count(),
                totals.min(),
                totals.max(),
                grandTotal);
    }

    private Aggregate aggregate(String title, List<ConvertedGroup> groups) {
        GroupTotals totals = totals(groups);
        return buildAggregate(title, totals.total(), totals.count(), totals.min(), totals.max());
    }

    private GroupTotals totals(List<ConvertedGroup> groups) {
        BigDecimal total = groups.stream()
                .map(ConvertedGroup::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = groups.stream()
                .mapToLong(ConvertedGroup::count)
                .sum();
        BigDecimal min = groups.stream()
                .map(ConvertedGroup::min)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal max = groups.stream()
                .map(ConvertedGroup::max)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return new GroupTotals(total, count, min, max);
    }

    private record ConvertedGroup(
            TransactionType type,
            PaymentMethod paymentMethod,
            Long categoryId,
            String categoryName,
            Long reasonId,
            String reasonDescription,
            BigDecimal total,
            long count,
            BigDecimal min,
            BigDecimal max) {
    }

    private record ConvertedDaily(
            LocalDate day,
            TransactionType type,
            BigDecimal amount,
            long count) {
    }

    private record GroupTotals(
            BigDecimal total,
            long count,
            BigDecimal min,
            BigDecimal max) {
    }

    @Override
    public String buildContextSummary(DashboardResponse dr) {
        TransactionsDashboard td = (TransactionsDashboard) dr;

        BigDecimal totalIncome = td.getDailyIncome().getAggregate().getTotalAmount();
        BigDecimal totalSpent   = td.getDailyExpenses().getAggregate().getTotalAmount();
        BigDecimal balance       = totalIncome.subtract(totalSpent);
        Aggregate  general       = td.getAggregate();

        return """
            === RESUMEN GENERAL ===
            Moneda       : %s
            Periodo      : %s
            Total movido : %s
            Operaciones  : %d
            Promedio/op  : %s
            Máximo       : %s
            Mínimo       : %s

            === MOVIMIENTOS ===
            Ingresos     : %s  (%s)
            Gastos       : %s  (%s)
            Balance neto : %s

            === TOP CATEGORÍAS DE GASTO ===
            %s

            === TOP CATEGORÍAS DE INGRESO ===
            %s

            === TOP MOTIVOS DE GASTO ===
            %s

            === TOP MOTIVOS DE INGRESO ===
            %s

            === DISTRIBUCIÓN POR TIPO ===
            %s

            === DISTRIBUCIÓN POR MÉTODO DE PAGO ===
            %s
            """.formatted(
                td.getCurrencyCode() != null ? td.getCurrencyCode() : "Sin definir",
                td.getDateRangeLabel(),
                general.getTotalAmount(),
                general.getOccurrenceCount(),
                general.getAverageAmount(),
                general.getMax(),
                general.getMin(),
                totalIncome, formatTimeLine(td.getDailyIncome()),
                totalSpent,   formatTimeLine(td.getDailyExpenses()),
                balance,
                formatRanking(td.getTopSpentCategories(), 5),
                formatRanking(td.getTopIncomeCategories(), 5),
                formatRanking(td.getTopSpentReasons(), 5),
                formatRanking(td.getTopIncomeReasons(), 5),
                formatRanking(td.getSummaryByType(), td.getSummaryByType().getCount()),
                formatRanking(td.getSummaryByPaymentMethod(), td.getSummaryByPaymentMethod().getCount())
        );
    }

    private RankingItem<CategorySummary> topCategoryItem(TransactionsDashboard td) {
        RankingItem<CategorySummary> top = null;
        for (Ranking<CategorySummary> ranking : List.of(
                td.getTopIncomeCategories(), td.getTopSpentCategories())) {
            if (ranking == null || ranking.getItems() == null) {
                continue;
            }
            for (RankingItem<CategorySummary> item : ranking.getItems()) {
                if (top == null || item.getAggregate().getAggregate().getTotalAmount()
                        .compareTo(top.getAggregate().getAggregate().getTotalAmount()) > 0) {
                    top = item;
                }
            }
        }
        return top;
    }

    @Override
    protected InsightSummary generateDefaultInsightSummary(DashboardResponse dataSource, int count) {
        TransactionsDashboard td = (TransactionsDashboard) dataSource;

        BigDecimal totalSpent   = td.getDailyExpenses().getAggregate().getTotalAmount();
        BigDecimal totalIncome = td.getDailyIncome().getAggregate().getTotalAmount();

        List<Insight> insights = new ArrayList<>();

        BigDecimal balance = totalIncome.subtract(totalSpent);
        boolean onDeficit = balance.compareTo(BigDecimal.ZERO) < 0;

        Insight balanceInsight = new Insight();
        balanceInsight.setSeverity(onDeficit ? Insight.InsightSeverity.CRITICAL : Insight.InsightSeverity.INFO);
        balanceInsight.setTitle(onDeficit ? "Balance mensual negativo" : "Balance mensual positivo");
        balanceInsight.setDescription("Ingresos: %s — Gastos: %s — Balance: %s".formatted(
                totalIncome, totalSpent, balance));
        balanceInsight.setAction(onDeficit
                ? "Revisa los gastos del periodo e identifica cuáles pueden reducirse."
                : "Considera destinar parte del excedente a ahorro o inversión.");
        insights.add(balanceInsight);

        RankingItem<CategorySummary> top = topCategoryItem(td);
        if (top != null) {
            BigDecimal share = top.getAggregate().getSharePercentage();
            boolean isHigh   = share.compareTo(new BigDecimal("50")) >= 0;
            TransactionType type = top.getAggregate().getFactor().getType();
            String base = TransactionType.SPENT == type ? "los gastos" : "los ingresos";

            Insight catInsight = new Insight();
            catInsight.setSeverity(isHigh ? Insight.InsightSeverity.WARNING : Insight.InsightSeverity.INFO);
            catInsight.setTitle("Categoría dominante: " + top.getAggregate().getFactorName());
            catInsight.setDescription("Concentra el %.1f%% de %s (%s).".formatted(
                    share, base, top.getAggregate().getAggregate().getTotalAmount()));

            if (isHigh) {
                if (TransactionType.SPENT == type) {
                    catInsight.setAction("Una sola categoría de gasto supera el 50%. Evalúa si hay oportunidad de reducirla.");
                } else {
                    catInsight.setAction("Una sola categoría de ingreso supera el 50%. Considera diversificar tus fuentes para reducir riesgos.");
                }
            } else {
                catInsight.setAction("Distribución de categorías dentro del rango normal.");
            }

            insights.add(catInsight);
        }

        InsightSummary summary = new InsightSummary();
        summary.setInsights(insights);
        summary.setInsightsCount(insights.size());
        return summary;
    }
}
