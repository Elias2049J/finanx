package com.elias.mismonedas.planning.domain.vo;

import java.math.BigDecimal;








public record SavingGoalTotals(BigDecimal accumulated, int transactionsCount) {

    public static SavingGoalTotals empty() {
        return new SavingGoalTotals(BigDecimal.ZERO, 0);
    }
}
