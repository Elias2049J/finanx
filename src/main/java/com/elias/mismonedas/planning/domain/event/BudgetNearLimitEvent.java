package com.elias.mismonedas.planning.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BudgetNearLimitEvent(
        Long budgetId,
        Long userId,
        String categoryName,
        BigDecimal spent,
        BigDecimal limitAmount,
        int percentAlert,
        OffsetDateTime lastTransactionAt,
        BigDecimal lastTransactionAmount,
        String lastTransactionMethod
) {
}
