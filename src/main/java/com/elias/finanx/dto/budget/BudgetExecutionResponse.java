package com.elias.finanx.dto.budget;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BudgetExecutionResponse extends BudgetResponse {
    private BigDecimal spentAmount;
    private BigDecimal remainingAmount;
    private BigDecimal executionPercentage;
}
