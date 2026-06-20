package com.elias.finanx.service.impl.analytics;

import com.elias.finanx.dto.ai.PromptRequest;
import com.elias.finanx.dto.analytics.component.*;
import com.elias.finanx.dto.analytics.dashboard.DashboardResponse;
import com.elias.finanx.dto.analytics.dashboard.TransactionsDashboard;
import com.elias.finanx.dto.category.CategorySummary;
import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.dto.date.PeriodResponse;
import com.elias.finanx.dto.reason.ReasonSummary;
import com.elias.finanx.dto.transaction.TransactionResponse;
import com.elias.finanx.entity.User;
import com.elias.finanx.entity.enums.PaymentMethod;
import com.elias.finanx.entity.enums.TransactionType;
import com.elias.finanx.mapper.CategoryMapper;
import com.elias.finanx.mapper.DateMapper;
import com.elias.finanx.mapper.ReasonMapper;
import com.elias.finanx.mapper.TransactionMapper;
import com.elias.finanx.repository.CategoryRepository;
import com.elias.finanx.repository.ReasonRepository;
import com.elias.finanx.repository.TransactionRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.InsightService;
import com.elias.finanx.service.analytics.TransactionAnalyticsService;
import com.elias.finanx.util.PeriodForQuery;
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

@Service
public class TransactionAnalyticsServiceImpl extends AnalyticsServiceImpl implements TransactionAnalyticsService {
    protected final TransactionRepository transactionRepository;
    protected final CategoryRepository categoryRepository;
    protected final ReasonRepository reasonRepository;
    protected final TransactionMapper tMapper;
    protected final ReasonMapper reasonMapper;
    protected final CategoryMapper categoryMapper;

    public TransactionAnalyticsServiceImpl(
            DateMapper dateMapper,
            UserRepository userRepository,
            InsightService insightService,
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            ReasonRepository reasonRepository, TransactionMapper tMapper, ReasonMapper reasonMapper, CategoryMapper categoryMapper
    ) {
        super(dateMapper, userRepository, insightService);
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.reasonRepository = reasonRepository;
        this.tMapper = tMapper;
        this.reasonMapper = reasonMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public DashboardResponse buildDashboard(PeriodRequest request, String prompt) throws HttpException, IOException {
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

        List<TransactionResponse> all = transactionRepository
                .findAllByUser_IdAndCreatedAtBetweenAndActive(
                        request.getUserId(),
                        pq.getStart(),
                        pq.getEnd(),
                        true
                )
                .stream()
                .map(tMapper::toResponse)
                .sorted(Comparator.comparing(TransactionResponse::getCreatedAt).reversed())
                .toList();


        PeriodResponse pr = dateMapper.toResponse(request);
        td.setPeriodResponse(pr);

        List<TransactionResponse> income = new ArrayList<>();
        List<TransactionResponse> spent = new ArrayList<>();

        for (TransactionResponse t : all) {
            if (t.getType() == TransactionType.SPENT) {
                spent.add(t);
            } else income.add(t);
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
                "Top " + topN + " motivos de ingresos",
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
        td.setDailyExpenses(expenses);
        td.setDailyIncome(incomes);
        td.setPeriodResponse(dateMapper.toResponse(request));
        td.setTopSpentReasons(rsRanking);
        td.setTopIncomeReasons(riRanking);
        td.setTopCategories(cRanking);
        td.setSummaryByCategory(summaryByCategory);
        td.setSummaryByType(summaryByType);
        td.setSummaryByPaymentMethod(summaryByPaymentMethod);
        td.setAggregate(generalAggregate);

        if (prompt != null && !prompt.isBlank()) {
            promptRequest.setUserPrompt(prompt);
            promptRequest.setContext(buildContextSummary(td));
            td.setInsightSummary(this.generateSmartInsightSummary(promptRequest, 3));
        } else td.setInsightSummary(this.generateDefaultInsightSummary(td, 3));

        return td;
    }

    @Override
    protected String buildContextSummary(DashboardResponse dr) {
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
            catInsight.setAction(isHigh
                    ? "Una sola categoría supera el 50% del gasto. Evalúa si hay oportunidad de reducirla."
                    : "Distribución de categorías dentro del rango normal.");
            insights.add(catInsight);
        }

        InsightSummary summary = new InsightSummary();
        summary.setInsights(insights);
        summary.setInsightsCount(insights.size());
        return summary;
    }
}