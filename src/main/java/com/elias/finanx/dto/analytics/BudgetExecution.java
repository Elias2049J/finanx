package com.elias.finanx.dto.analytics;

import com.elias.finanx.entity.enums.BudgetState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BudgetExecution {
    private Long budgetId;
    private String budgetDescription;
    private BudgetState budgetState;

    private boolean notificationEnabled;
    private Integer alertThresholdPercentage;

    private LocalDate budgetStartDate;
    private LocalDate budgetEndDate;

    private BudgetAnalytics.CategoryDTO category;

    private BigDecimal budgetLimitAmount;
    private BigDecimal spentAmount;
    private BigDecimal remainingAmount;

    private BigDecimal executionPercentage;
    private BudgetAnalytics.BudgetHealth budgetHealth;
}