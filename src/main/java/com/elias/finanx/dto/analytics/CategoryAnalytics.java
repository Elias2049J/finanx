package com.elias.finanx.dto.analytics;

import com.elias.finanx.entity.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryAnalytics {
    private Long userId;
    private AnalyticsPeriod period;

    private Map<TransactionType, BigDecimal> totalAmountByTransactionType;

    private List<RankingItem<CategorySummary>> topSpentCategories;
    private List<RankingItem<CategorySummary>> topIncomeCategories;

    private List<CategoryAggregate> categoryAggregates;
}
