package com.elias.mismonedas.insights.service.impl.analytics;

import com.elias.mismonedas.wallets.repository.WalletRepository;
import com.elias.mismonedas.catalog.dto.category.CategorySummary;
import com.elias.mismonedas.catalog.dto.reason.ReasonSummary;
import com.elias.mismonedas.catalog.mapper.CategoryMapper;
import com.elias.mismonedas.catalog.mapper.ReasonMapper;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.repository.ReasonRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.insights.dto.analytics.component.*;
import com.elias.mismonedas.insights.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.insights.dto.analytics.dashboard.TransactionsDashboard;
import com.elias.mismonedas.insights.service.analytics.TransactionAnalyticsService;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.mapper.TransactionMapper;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.repository.TransactionSpecifications;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.domain.vo.Money;
import com.elias.mismonedas.shared.exception.CurrencyConversionException;
import com.elias.mismonedas.shared.mapper.DateMapper;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service("transactionAnalyticsService")
public class TransactionAnalyticsServiceImpl extends AnalyticsServiceImpl implements TransactionAnalyticsService {
    protected final TransactionRepository transactionRepository;
    protected final CategoryRepository categoryRepository;
    protected final ReasonRepository reasonRepository;
    protected final TransactionMapper tMapper;
    protected final ReasonMapper reasonMapper;
    protected final CategoryMapper categoryMapper;
    protected final CurrencyConverter currencyConverter;

    public TransactionAnalyticsServiceImpl(
            DateMapper dateMapper,
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            ReasonRepository reasonRepository,
            TransactionMapper tMapper,
            ReasonMapper reasonMapper,
            CategoryMapper categoryMapper,
            WalletRepository walletRepository,
            CurrencyConverter currencyConverter,
            SecurityService securityService) {
        super(dateMapper, userRepository, securityService, walletRepository);
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.reasonRepository = reasonRepository;
        this.tMapper = tMapper;
        this.reasonMapper = reasonMapper;
        this.categoryMapper = categoryMapper;
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
        var periodStart = coveredPeriod.startInclusive(zoneId);
        var periodEnd = coveredPeriod.endExclusive(zoneId);
        LocalDateTime createdAt = OffsetDateTime.now().atZoneSameInstant(zoneId).toLocalDateTime();
        String baseCurrency = resolveBaseCurrency(u);

        if (walletId != null) {
            walletRepository.findByIdAndUser_Id(walletId, userId)
                    .orElseThrow(() -> new EntityNotFoundException("Wallet not found: " + walletId));
        }

        List<String> missingRates = new ArrayList<>();
        Set<String> failedCurrencies = new HashSet<>();

        List<TransactionResponse> all = transactionRepository.findAll(TransactionSpecifications.list(
                        userId, true, null, walletId, null, periodStart, periodEnd, null))
                .stream()
                .map(tMapper::toResponse)
                .map(t -> convertToBase(t, baseCurrency, failedCurrencies, missingRates))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TransactionResponse::getCreatedAt).reversed())
                .toList();

        log.info("Period for query: {} to {}", periodStart, periodEnd);

        BigDecimal balance = BigDecimal.ZERO;

        String dateRangeLabel = dateMapper.dateRangeLabel(coveredPeriod);
        List<String> uniqueMissingRates = missingRates.stream().distinct().sorted().toList();
        td.setDateRangeLabel(dateRangeLabel);
        td.setCurrencyCode(baseCurrency);
        td.setPartial(!uniqueMissingRates.isEmpty());
        td.setMissingRates(uniqueMissingRates);

        List<TransactionResponse> income = new ArrayList<>();
        List<TransactionResponse> spent = new ArrayList<>();

        for (TransactionResponse t : all) {
            if (t.getType() == TransactionType.SPENT) {
                spent.add(t);
                balance = balance.subtract(t.getAmount());
            } else {
                income.add(t);
                balance = balance.add(t.getAmount());
            }
        }

        com.elias.mismonedas.insights.dto.analytics.component.TimeLine<TransactionResponse> expenses = this.buildTimeLine(
                coveredPeriod,
                "Gastos del periodo :" + dateRangeLabel,
                spent,
                t -> t.getCreatedAt().toLocalDate(),
                TransactionResponse::getAmount,
                Comparator.comparing(TransactionResponse::getCreatedAt));

        com.elias.mismonedas.insights.dto.analytics.component.TimeLine<TransactionResponse> incomes = this.buildTimeLine(
                coveredPeriod,
                "Ingresos del periodo :" + dateRangeLabel,
                income,
                t -> t.getCreatedAt().toLocalDate(),
                TransactionResponse::getAmount,
                Comparator.comparing(TransactionResponse::getCreatedAt));

        BigDecimal totalGeneral = this.accumulate(all.stream().map(TransactionResponse::getAmount).toList());
        BigDecimal totalIncome = this.accumulate(income.stream().map(TransactionResponse::getAmount).toList());
        BigDecimal totalSpent = this.accumulate(spent.stream().map(TransactionResponse::getAmount).toList());

        List<AggregateBy<ReasonSummary>> spentByReason = spent.stream()
                .collect(Collectors.groupingBy(reasonMapper::toReasonSummary))
                .entrySet().stream()
                .map(entry -> buildAggregateBy(
                        entry.getKey(),
                        entry.getKey().getDescription(),
                        entry.getValue().stream().map(TransactionResponse::getAmount).toList(),
                        totalSpent
                ))
                .toList();

        List<AggregateBy<ReasonSummary>> incomeByReason = income.stream()
                .collect(Collectors.groupingBy(reasonMapper::toReasonSummary))
                .entrySet().stream()
                .map(entry -> buildAggregateBy(
                        entry.getKey(),
                        entry.getKey().getDescription(),
                        entry.getValue().stream().map(TransactionResponse::getAmount).toList(),
                        totalIncome
                ))
                .toList();

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

        List<AggregateBy<PaymentMethod>> aggsByPaymentMethod = all.stream()
                .collect(Collectors.groupingBy(TransactionResponse::getPaymentMethod))
                .entrySet().stream()
                .map(entry -> buildAggregateBy(
                        entry.getKey(),
                        entry.getKey().getDisplayName(),
                        entry.getValue().stream().map(TransactionResponse::getAmount).toList(),
                        totalGeneral
                ))
                .toList();

        List<AggregateBy<CategorySummary>> aggsByCategory = all.stream()
                .collect(Collectors.groupingBy(categoryMapper::toSummary))
                .entrySet().stream()
                .map(entry -> buildAggregateBy(
                        entry.getKey(),
                        entry.getKey().getName(),
                        entry.getValue().stream().map(TransactionResponse::getAmount).toList(),
                        totalGeneral
                ))
                .toList();

        List<AggregateBy<CategorySummary>> spentByCategory = spent.stream()
                .collect(Collectors.groupingBy(categoryMapper::toSummary))
                .entrySet().stream()
                .map(entry -> buildAggregateBy(
                        entry.getKey(),
                        entry.getKey().getName(),
                        entry.getValue().stream().map(TransactionResponse::getAmount).toList(),
                        totalSpent
                ))
                .toList();

        List<AggregateBy<CategorySummary>> incomeByCategory = income.stream()
                .collect(Collectors.groupingBy(categoryMapper::toSummary))
                .entrySet().stream()
                .map(entry -> buildAggregateBy(
                        entry.getKey(),
                        entry.getKey().getName(),
                        entry.getValue().stream().map(TransactionResponse::getAmount).toList(),
                        totalIncome
                ))
                .toList();

        List<AggregateBy<TransactionType>> aggsByType = all.stream()
                .collect(Collectors.groupingBy(TransactionResponse::getType))
                .entrySet().stream()
                .map(entry -> buildAggregateBy(
                        entry.getKey(),
                        entry.getKey().getDisplayName(),
                        entry.getValue().stream().map(TransactionResponse::getAmount).toList(),
                        totalGeneral
                ))
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

        Aggregate generalAggregate = buildAggregate(
                "Resumen general de movimientos del periodo: " + dateRangeLabel,
                all.stream().map(TransactionResponse::getAmount).toList()
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

    private TransactionResponse convertToBase(TransactionResponse transaction,
                                              String baseCurrency,
                                              Set<String> failedCurrencies,
                                              List<String> missingRates) {
        if (transaction.getAmount() == null || baseCurrency == null) {
            return transaction;
        }

        String sourceCurrency = transaction.getWalletCurrency();
        if (sourceCurrency == null || sourceCurrency.equals(baseCurrency)) {
            transaction.setWalletCurrency(baseCurrency);
            return transaction;
        }

        if (failedCurrencies.contains(sourceCurrency)) {
            missingRates.add(sourceCurrency + "->" + baseCurrency);
            return null;
        }

        try {
            Money converted = currencyConverter.convert(
                    Money.of(transaction.getAmount(), sourceCurrency),
                    baseCurrency);
            transaction.setAmount(converted.amount());
            transaction.setWalletCurrency(baseCurrency);
            return transaction;
        } catch (CurrencyConversionException ex) {
            failedCurrencies.add(sourceCurrency);
            missingRates.add(sourceCurrency + "->" + baseCurrency);
            log.warn("Sin tasa {}->{}: se excluye la transaccion del consolidado", sourceCurrency, baseCurrency);
            return null;
        }
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
