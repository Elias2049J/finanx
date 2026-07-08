package com.elias.mismonedas.service.impl.analytics;

import com.elias.mismonedas.dto.ai.PromptRequest;
import com.elias.mismonedas.dto.analytics.component.*;
import com.elias.mismonedas.dto.analytics.dashboard.DashboardResponse;
import com.elias.mismonedas.dto.analytics.dashboard.TransactionsDashboard;
import com.elias.mismonedas.dto.category.CategorySummary;
import com.elias.mismonedas.dto.date.PeriodRequest;
import com.elias.mismonedas.dto.date.PeriodResponse;
import com.elias.mismonedas.dto.reason.ReasonSummary;
import com.elias.mismonedas.dto.transaction.TransactionResponse;
import com.elias.mismonedas.entity.User;
import com.elias.mismonedas.entity.Wallet;
import com.elias.mismonedas.entity.enums.PaymentMethod;
import com.elias.mismonedas.entity.enums.TransactionType;
import com.elias.mismonedas.mapper.CategoryMapper;
import com.elias.mismonedas.mapper.DateMapper;
import com.elias.mismonedas.mapper.ReasonMapper;
import com.elias.mismonedas.mapper.TransactionMapper;
import com.elias.mismonedas.repository.*;
import com.elias.mismonedas.service.analytics.TransactionAnalyticsService;
import com.elias.mismonedas.util.PeriodForQuery;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    protected final WalletRepository walletRepository;

    public TransactionAnalyticsServiceImpl(
            DateMapper dateMapper,
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            ReasonRepository reasonRepository,
            TransactionMapper tMapper,
            ReasonMapper reasonMapper,
            CategoryMapper categoryMapper,
            WalletRepository walletRepository) {
        super(dateMapper, userRepository);
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.reasonRepository = reasonRepository;
        this.tMapper = tMapper;
        this.reasonMapper = reasonMapper;
        this.categoryMapper = categoryMapper;
        this.walletRepository = walletRepository;
    }

    @Override
    public DashboardResponse buildDashboard(PeriodRequest request) throws HttpException, IOException {
        TransactionsDashboard td = new TransactionsDashboard();
        PromptRequest promptRequest = new PromptRequest();

        int topN = 5;
        String title = "Dashboard de Movimientos: " +
                "Gastos e Ingresos Diarios, " +
                "Ranking de Categorías y Motivos, y" +
                " Resúmenes por Categoría, Método de Pago y Tipo";

        User u = this.getUser(request.getUserId());
        ZoneId zoneId = u.getTimeZone().toZoneId();
        PeriodForQuery pq = dateMapper.toSmartMonthlyPeriod(request.getStart(), request.getEnd(), zoneId, request.getUserId());
        LocalDateTime createdAt = OffsetDateTime.now().atZoneSameInstant(zoneId).toLocalDateTime();

        Wallet primaryWallet = walletRepository.findByUserIdAndPrimaryTrue(request.getUserId())
                .orElse(null);

        List<TransactionResponse> all;
        if (primaryWallet != null) {
            Long walletId = primaryWallet.getId();
            all = transactionRepository
                    .findAllByWallet_IdAndCreatedAtBetweenAndActive(
                            walletId,
                            pq.getStart(),
                            pq.getEnd(),
                            true
                    )
                    .stream()
                    .map(tMapper::toResponse)
                    .sorted(Comparator.comparing(TransactionResponse::getCreatedAt).reversed())
                    .toList();
        } else {
            all = List.of();
        }

        log.info("Period for query: {}", pq);

        BigDecimal balance = BigDecimal.ZERO;

        PeriodResponse pr = dateMapper.toResponse(
                new PeriodRequest(
                        pq.getStart().atZoneSameInstant(zoneId).toLocalDate(),
                        pq.getEnd().atZoneSameInstant(zoneId).toLocalDate(), u.getId()
                ));
        td.setPeriodResponse(pr);

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

        TimeLine<TransactionResponse> expenses = this.buildTimeLine(
                request,
                "Gastos del periodo :" + pr.getReadableString(),
                spent,
                t -> t.getCreatedAt().toLocalDate(),
                TransactionResponse::getAmount,
                Comparator.comparing(TransactionResponse::getCreatedAt));

        TimeLine<TransactionResponse> incomes = this.buildTimeLine(
                request,
                "Ingresos del periodo :" + pr.getReadableString(),
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

        Ranking<CategorySummary> cRanking = this.buildRanking(
                "Top " + topN + " categorías con más dinero acumulado",
                aggsByCategory,
                agg -> agg.getAggregate().getTotalAmount(),
                topN
        );

        Ranking<CategorySummary> summaryByCategory = this.buildRanking(
                "Resumen de movimientos por categoría, ordenado por montos",
                aggsByCategory,
                agg -> agg.getAggregate().getTotalAmount(),
                aggsByCategory.size()
        );

        Ranking<PaymentMethod> summaryByPaymentMethod = this.buildRanking(
                "Resumen de movimientos por método de pago, ordenado por monto",
                aggsByPaymentMethod,
                agg -> agg.getAggregate().getTotalAmount(),
                aggsByCategory.size()
        );

        Ranking<TransactionType> summaryByType = this.buildRanking(
                "Resumen de movimientos por tipo, ordenado por monto",
                aggsByType,
                agg -> agg.getAggregate().getTotalAmount(),
                aggsByCategory.size()
        );

        Aggregate generalAggregate = buildAggregate(
                "Resumen general de movimientos del periodo: " + pr.getReadableString(),
                all.stream().map(TransactionResponse::getAmount).toList()
        );

        td.setTitle(title);
        td.setCreatedAt(createdAt);
        td.setBalance(balance);
        td.setDailyExpenses(expenses);
        td.setDailyIncome(incomes);
        td.setPeriodResponse(pr);
        td.setTopSpentReasons(rsRanking);
        td.setTopIncomeReasons(riRanking);
        td.setTopCategories(cRanking);
        td.setSummaryByCategory(summaryByCategory);
        td.setSummaryByType(summaryByType);
        td.setSummaryByPaymentMethod(summaryByPaymentMethod);
        td.setAggregate(generalAggregate);
        td.setInsightSummary(this.generateDefaultInsightSummary(td, 2));

        return td;
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

            === TOP CATEGORÍAS (por monto acumulado) ===
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
                td.getPeriodResponse().getReadableString(),
                general.getTotalAmount(),
                general.getOccurrenceCount(),
                general.getAverageAmount(),
                general.getMax(),
                general.getMin(),
                totalIncome, formatTimeLine(td.getDailyIncome()),
                totalSpent,   formatTimeLine(td.getDailyExpenses()),
                balance,
                formatRanking(td.getTopCategories(), 5),
                formatRanking(td.getTopSpentReasons(), 5),
                formatRanking(td.getTopIncomeReasons(), 5),
                formatRanking(td.getSummaryByType(), td.getSummaryByType().getCount()),
                formatRanking(td.getSummaryByPaymentMethod(), td.getSummaryByPaymentMethod().getCount())
        );
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

        if (td.getTopCategories() != null && !td.getTopCategories().getItems().isEmpty()) {
            RankingItem<CategorySummary> top = td.getTopCategories().getItems().getFirst();
            BigDecimal share = top.getAggregate().getSharePercentage();
            boolean isHigh   = share.compareTo(new BigDecimal("50")) >= 0;

            Insight catInsight = new Insight();
            catInsight.setSeverity(isHigh ? Insight.InsightSeverity.WARNING : Insight.InsightSeverity.INFO);
            catInsight.setTitle("Categoría dominante: " + top.getAggregate().getFactorName());
            catInsight.setDescription("Concentra el %.1f%% del total movido (%s).".formatted(
                    share, top.getAggregate().getAggregate().getTotalAmount()));

            TransactionType type = top.getAggregate().getFactor().getType();

            if (isHigh) {
                if (TransactionType.SPENT == type) {
                    catInsight.setAction("Una sola categoría de gasto supera el 50%. Evalúa si hay oportunidad de reducirla.");
                } else if (TransactionType.INCOME == type) {
                    catInsight.setAction("Una sola categoría de ingreso supera el 50%. Considera diversificar tus fuentes para reducir riesgos.");
                } else {
                    catInsight.setAction("Una sola categoría concentra más del 50%. Evalúa si esa concentración es saludable.");
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