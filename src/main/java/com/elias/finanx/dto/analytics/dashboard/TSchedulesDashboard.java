package com.elias.finanx.dto.analytics.dashboard;

import com.elias.finanx.dto.analytics.ScheduledProjection;
import com.elias.finanx.dto.analytics.component.Insight;
import com.elias.finanx.entity.enums.RecurrenceType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TSchedulesDashboard extends DashboardResponse {
    private long activeScheduledTransactionCount;
    private Map<RecurrenceType, Long> activeCountByRecurrenceType;

    private LocalDate projectionStart;
    private LocalDate projectionEnd;

    private BigDecimal projectedIncomeAmount;
    private BigDecimal projectedSpentAmount;
    private BigDecimal projectedNetAmount;

    private List<ScheduledProjection> scheduledProjections;
    private List<Insight> insights;
}
