package com.elias.mismonedas.planning.domain.event;

public record BudgetRegisteredEvent(
        Long budgetId,
        Long userId) {
}
