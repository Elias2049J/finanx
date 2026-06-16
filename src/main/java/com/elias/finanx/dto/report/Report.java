package com.elias.finanx.dto.report;

import com.elias.finanx.dto.date.PeriodResponse;
import com.elias.finanx.dto.analytics.dashboard.*;
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
    private PeriodResponse periodResponse;
    private Long userId;
    private LocalDateTime createdAt;
}
