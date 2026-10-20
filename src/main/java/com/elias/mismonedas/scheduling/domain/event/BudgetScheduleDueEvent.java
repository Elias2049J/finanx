package com.elias.mismonedas.scheduling.domain.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BudgetScheduleDueEvent(
        Long scheduleId,
        Long userId,
        Long categoryId,
        boolean alertable,
        Integer percentAlert,
        BigDecimal limitAmount,
        String description,
        OffsetDateTime start,
        OffsetDateTime end) {
}
