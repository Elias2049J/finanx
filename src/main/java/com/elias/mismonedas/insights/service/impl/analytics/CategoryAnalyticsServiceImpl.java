package com.elias.mismonedas.insights.service.impl.analytics;

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
import com.elias.mismonedas.insights.service.analytics.CategoryAnalyticsService;
import com.elias.mismonedas.ledger.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.mapper.TransactionMapper;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.repository.TransactionSpecifications;
import com.elias.mismonedas.scheduling.domain.enums.RecurrenceType;
import com.elias.mismonedas.shared.dto.date.PeriodRequest;
import com.elias.mismonedas.shared.dto.date.PeriodResponse;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import com.elias.mismonedas.shared.mapper.DateMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryAnalyticsServiceImpl extends AnalyticsServiceImpl implements CategoryAnalyticsService {
    protected final TransactionRepository transactionRepository;
    protected final CategoryRepository categoryRepository;
    protected final ReasonRepository reasonRepository;
    protected final TransactionMapper tMapper;
    protected final ReasonMapper reasonMapper;
    protected final CategoryMapper categoryMapper;

    public CategoryAnalyticsServiceImpl(
            DateMapper dateMapper,
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            ReasonRepository reasonRepository, TransactionMapper tMapper, ReasonMapper reasonMapper, CategoryMapper categoryMapper,
            SecurityService securityService
    ) {
        super(dateMapper, userRepository, securityService);
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.reasonRepository = reasonRepository;
        this.tMapper = tMapper;
        this.reasonMapper = reasonMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public DashboardResponse buildDashboard(PeriodRequest request, Long userId) {
        TransactionsDashboard td = new TransactionsDashboard();

        int topN = 5;
        String title = "Dashboard de Transacciones: " +
                "Gastos e Ingresos Diarios, " +
                "Ranking de Categorías y Motivos, y" +
                " Resúmenes por Categoría, Método de Pago y Tipo";

        User u = this.getUser(userId);
        ZoneId zoneId = u.getTimeZone().toZoneId();
        DateRange period = new DateRange(request.getStart(), request.getEnd()).coveringMonths();
        var periodStart = period.startInclusive(zoneId);
        var periodEnd = period.endExclusive(zoneId);
        LocalDateTime createdAt = OffsetDateTime.now().atZoneSameInstant(zoneId).toLocalDateTime();

        List<TransactionResponse> all = transactionRepository.findAll(TransactionSpecifications.list(
                        userId, true, null, null, null, periodStart, periodEnd))
                .stream()
                .map(tMapper::toResponse)
                .sorted(Comparator.comparing(TransactionResponse::getCreatedAt).reversed())
                .toList();


        PeriodResponse pr = dateMapper.toResponse(request);
        td.setPeriodResponse(pr);

        List<TransactionResponse> income = new ArrayList<>();
        List<TransactionResponse> spent = new ArrayList<>();

        for (TransactionResponse t: all) {
            if (t.getType() == TransactionType.SPENT) {
                spent.add(t);
            }
            else income.add(t);
        }

        TimeLine<TransactionResponse> expenses = this.buildTimeLine(
                request,
                "Gastos del periodo :"+ pr.getReadableString(),
                spent,
                t -> t.getCreatedAt().toLocalDate(),
                TransactionResponse::getAmount,
                Comparator.comparing(TransactionResponse::getCreatedAt));

        TimeLine<TransactionResponse> incomes = this.buildTimeLine(
                request,
                "Ingresos del periodo :"+ pr.getReadableString(),
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
                "Top " +topN+" motivos de gastos",
                spentByReason,
                agg -> agg.getAggregate().getTotalAmount(),
                topN
        );

        Ranking<ReasonSummary> riRanking = this.buildRanking(
                "Top " +topN+" motivos de ingresos",
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
        return td;
    }

    @Override
    protected InsightSummary generateDefaultInsightSummary(DashboardResponse dataSource, int count) {
        return null;
    }

    @Override
    public String buildContextSummary(DashboardResponse dataSource) {
        return "";
    }

    private Long getTransactionAggregatesByPaymentMethod(Long userId, PeriodResponse periodResponse) {
        return null;
    }

    private Long getTransactionAggregatesByType(Long userId, PeriodResponse periodResponse) {
        return null;
    }


    private InsightSummary getTransactionInsights(PeriodRequest request) {
        return null;
    }

    private PaymentMethod getMostUsedPaymentMethod(Long userId, PeriodResponse periodResponse) {
        return null;
    }


    private Long getActiveScheduledTransactionCount(Long userId) {
        return null;
    }

    private Map<RecurrenceType, Long> getActiveScheduledCountByRecurrenceType(Long userId) {
        return Map.of();
    }
}

