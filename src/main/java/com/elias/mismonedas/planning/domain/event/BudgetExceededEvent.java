package com.elias.mismonedas.planning.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BudgetExceededEvent(
        Long budgetId,
        Long userId,
        String categoryName,
        BigDecimal spent,
        BigDecimal limitAmount,
        String currencyCode,
        OffsetDateTime lastTransactionAt,
        BigDecimal lastTransactionAmount,
        String lastTransactionMethod
) {
}
