package com.elias.mismonedas.planning.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record SavingGoalTotals(BigDecimal accumulated, int transactionsCount, boolean partial, List<String> missingRates) {

    public SavingGoalTotals {
        missingRates = missingRates == null ? List.of() : List.copyOf(missingRates);
    }

    public static SavingGoalTotals empty() {
        return new SavingGoalTotals(BigDecimal.ZERO, 0, false, List.of());
    }
}
