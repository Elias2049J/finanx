package com.elias.finanx.dto.analytics;

import com.elias.finanx.entity.enums.PaymentMethod;
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
public class TransactionAnalytics {
    private Long userId;
    private AnalyticsPeriod period;

    private BigDecimal totalIncomeAmount;
    private BigDecimal totalSpentAmount;
    private BigDecimal netAmount;

    private Map<TransactionType, TransactionAggregate> aggregatesByTransactionType;
    private Map<PaymentMethod, TransactionAggregate> aggregatesByPaymentMethod;

    private PaymentMethod mostUsedPaymentMethod;
    private List<RankingItem<PaymentMethod>> topPaymentMethods;

    private List<TimeSeriesPoint> dailyNetAmounts;

    private List<Insight> insights;
}
