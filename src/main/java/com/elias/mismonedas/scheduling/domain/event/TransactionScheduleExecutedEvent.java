package com.elias.mismonedas.scheduling.domain.event;

public record TransactionScheduleExecutedEvent(
        Long scheduleId,
        Long userId) {
}
