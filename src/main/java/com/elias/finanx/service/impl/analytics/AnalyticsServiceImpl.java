package com.elias.finanx.service.impl.analytics;

import com.elias.finanx.dto.ai.PromptRequest;
import com.elias.finanx.dto.analytics.TimeBoundList;
import com.elias.finanx.dto.analytics.dashboard.DashboardResponse;
import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.dto.analytics.component.*;
import com.elias.finanx.dto.date.PeriodResponse;
import com.elias.finanx.entity.User;
import com.elias.finanx.mapper.DateMapper;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.InsightService;
import com.elias.finanx.service.analytics.AnalyticsService;
import org.apache.http.HttpException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.math.RoundingMode.HALF_UP;

@Service
public abstract class AnalyticsServiceImpl implements AnalyticsService {
    protected final DateMapper dateMapper;
    protected final UserRepository userRepository;
    protected final InsightService insightService;

    public AnalyticsServiceImpl(DateMapper dateMapper, UserRepository userRepository, InsightService insightService) {
        this.dateMapper = dateMapper;
        this.userRepository = userRepository;
        this.insightService = insightService;
    }

    @Transactional(readOnly = true)
    @Override
    public abstract DashboardResponse buildDashboard(PeriodRequest request, String prompt) throws HttpException, IOException;

    protected Aggregate buildAggregate(
            String title,
            List<BigDecimal> amounts) {

        BigDecimal total   = amounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = amounts.isEmpty() ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(amounts.size()), 2, HALF_UP);
        BigDecimal max     = amounts.stream()
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal min     = amounts.stream()
                .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        return Aggregate.builder()
                .title(title)
                .totalAmount(total)
                .averageAmount(average)
                .occurrenceCount(amounts.size())
                .max(max)
                .min(min)
                .build();
    }


    protected <F> AggregateBy<F> buildAggregateBy(
            F factor,
            String factorName,
            List<BigDecimal> amounts,
            BigDecimal grandTotal) {

        Aggregate base = buildAggregate("por "+factorName, amounts);

        BigDecimal share = grandTotal.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : base.getTotalAmount()
                .multiply(BigDecimal.valueOf(100))
                .divide(grandTotal, 2, RoundingMode.HALF_UP);

        AggregateBy<F> aggregateBy = new AggregateBy<>();
        aggregateBy.setAggregate(base);
        aggregateBy.setFactor(factor);
        aggregateBy.setFactorName(factorName);
        aggregateBy.setSharePercentage(share);

        return aggregateBy;
    }


    protected <F> Ranking<F> buildRanking(
            String title,
            List<AggregateBy<F>> aggregates,
            Function<AggregateBy<F>, BigDecimal> metricExtractor,
            int topN
    ) {

        List<AggregateBy<F>> sorted = aggregates.stream()
                .sorted(Comparator.comparing(metricExtractor).reversed())
                .limit(topN)
                .toList();

        List<RankingItem<F>> items = IntStream.range(0, sorted.size())
                .mapToObj(i -> {
                    AggregateBy<F> agg = sorted.get(i);
                    RankingItem<F> item = new RankingItem<>();
                    item.setRank(i + 1);
                    item.setMetricValue(metricExtractor.apply(agg));
                    item.setAggregate(agg);
                    return item;
                })
                .toList();

        Ranking<F> ranking = new Ranking<>();
        ranking.setTitle(title);
        ranking.setCount(topN);
        ranking.setItems(items);

        return ranking;
    }


    protected <T> TimeSeriesPoint<T> buildTimeSeriesPoint(
            LocalDate date,
            List<BigDecimal> amounts,
            List<T> context,
            Comparator<T> contextSorter
    ) {
        BigDecimal total = amounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<T> orderedContext = contextSorter == null
                ? context
                : context.stream().sorted(contextSorter).toList();

        return new TimeSeriesPoint<>(
                date,
                total,
                amounts.size(),
                orderedContext
        );
    }


    protected <T> TimeLine<T> buildTimeLine(
            PeriodRequest dateRange,
            String title,
            List<T> items,
            Function<T, LocalDate> dateResolver,
            Function<T, BigDecimal> amountResolver,
            Comparator<T> contextComparator) {

        Map<LocalDate, List<BigDecimal>> amountsByDate = items.stream()
                .collect(Collectors.groupingBy(
                        dateResolver,
                        Collectors.mapping(amountResolver, Collectors.toList())
                ));

        Map<LocalDate, List<T>> itemsByDate = items.stream()
                .collect(Collectors.groupingBy(dateResolver));

        List<TimeSeriesPoint<T>> points = amountsByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> buildTimeSeriesPoint(
                        entry.getKey(),
                        entry.getValue(),
                        itemsByDate.getOrDefault(entry.getKey(), List.of()),
                        contextComparator
                ))
                .toList();

        List<BigDecimal> allAmounts = points.stream()
                .map(TimeSeriesPoint::getAmount)
                .toList();

        Aggregate summary = buildAggregate(title, allAmounts);
        PeriodResponse periodResponse = dateMapper.toResponse(dateRange);

        TimeLine<T> timeLine = new TimeLine<>();
        timeLine.setAggregate(summary);
        timeLine.setTitle(title);
        timeLine.setPeriodResponse(periodResponse);
        timeLine.setPoints(points);
        timeLine.setCount(points.size());
        return timeLine;
    }



    protected <T> TimeBoundList<T> buildTimeBound(List<T> items, PeriodRequest request) {
        TimeBoundList<T> tb = new TimeBoundList<>();
        tb.setCount(items.size());
        tb.setItems(items);
        tb.setPeriodResponse(dateMapper.toResponse(request));
        return tb;
    }

    protected User getUser(Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    protected BigDecimal getPercentageOfTotal(BigDecimal value, BigDecimal total) {
        return value
                .divide(total, 4, HALF_UP)
                .multiply(new BigDecimal("100.00"));
    }

    protected <T> Map<LocalDate, List<BigDecimal>> buildAmountByDate(
            List<T> items,
            Function<T, LocalDate> dateResolver,
            Function<T, BigDecimal> amountResolver) {
        return items.stream()
                .collect(Collectors.groupingBy(
                        dateResolver,
                        Collectors.mapping(amountResolver, Collectors.toList())
                ));
    }

    protected <T> Map<LocalDate, List<T>> buildMapByDate(
            List<T> items,
            Function<T, LocalDate> dateResolver
    ) {
        return items.stream()
                .collect(Collectors.groupingBy(
                        dateResolver,
                        Collectors.toList()
                ));
    }

    protected BigDecimal accumulate(Iterable<BigDecimal> amounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            if (amount != null) {
                total = total.add(amount);
            }
        }
        return total;
    }

    protected InsightSummary generateSmartInsightSummary(PromptRequest prompt, int count) throws HttpException, IOException {
        return insightService.getInsights(prompt, count);
    }

    protected abstract InsightSummary generateDefaultInsightSummary(DashboardResponse dataSource, int count);

    protected abstract String buildContextSummary(DashboardResponse dataSource);

    protected <F> String formatRanking(Ranking<F> ranking, int limit) {
        if (ranking == null || ranking.getItems() == null || ranking.getItems().isEmpty())
            return "  Sin datos";
        return ranking.getItems().stream()
                .limit(limit)
                .map(item -> "  #%d %s → %s (%.1f%%)".formatted(
                        item.getRank(),
                        item.getAggregate().getFactorName(),
                        item.getAggregate().getAggregate().getTotalAmount(),
                        item.getAggregate().getSharePercentage()
                ))
                .collect(Collectors.joining("\n"));
    }

    protected <T> String formatTimeLine(TimeLine<T> timeLine) {
        if (timeLine == null) return "Sin datos";
        Aggregate agg = timeLine.getAggregate();
        return "total=%s, promedio=%s, días_activos=%d".formatted(
                agg.getTotalAmount(),
                agg.getAverageAmount(),
                timeLine.getCount()
        );
    }
}
