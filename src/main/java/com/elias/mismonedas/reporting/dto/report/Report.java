package com.elias.mismonedas.reporting.dto.report;

import com.elias.mismonedas.insights.dto.analytics.dashboard.BudgetsDashboard;
import com.elias.mismonedas.insights.dto.analytics.dashboard.SavingGoalsDashboard;
import com.elias.mismonedas.insights.dto.analytics.dashboard.TSchedulesDashboard;
import com.elias.mismonedas.insights.dto.analytics.dashboard.TransactionsDashboard;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TransactionsDashboard.class, name = "BALANCE"),
        @JsonSubTypes.Type(value = SavingGoalsDashboard.class, name = "SAVINGS"),
        @JsonSubTypes.Type(value = TSchedulesDashboard.class, name = "TSCHEDULES"),
        @JsonSubTypes.Type(value = BudgetsDashboard.class, name = "BUDGETS")
})
public abstract class Report {
    private String dateRangeLabel;
    private Long userId;
    private LocalDateTime createdAt;
}
