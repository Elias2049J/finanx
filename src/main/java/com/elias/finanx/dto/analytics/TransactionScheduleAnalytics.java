package com.elias.finanx.dto.analytics;

import com.elias.finanx.entity.enums.RecurrenceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Analytics y proyecciones basadas en movimientos programados.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionScheduleAnalytics {
    private Long userId;
    private OffsetDateTime generatedAt;

    private long activeScheduledTransactionCount;
    private Map<RecurrenceType, Long> activeCountByRecurrenceType;

    private OffsetDateTime projectionStart;
    private OffsetDateTime projectionEnd;

    private BigDecimal projectedIncomeAmount;
    private BigDecimal projectedSpentAmount;
    private BigDecimal projectedNetAmount;

    private List<ScheduledProjection> scheduledProjections;
    private List<Insight> insights;
}
