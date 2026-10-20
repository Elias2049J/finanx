package com.elias.mismonedas.scheduling.domain.event;

public record BudgetScheduleExecutedEvent(
        Long scheduleId,
        Long userId,
        Long budgetId) {
}
