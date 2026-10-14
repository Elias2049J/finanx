package com.elias.mismonedas.planning.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SavingGoalCompletedEvent(
        Long savingGoalId,
        Long userId,
        String description,
        BigDecimal accumulated,
        BigDecimal targetAmount,
        String currencyCode,
        OffsetDateTime lastTransactionAt,
        BigDecimal lastTransactionAmount
) {
}
