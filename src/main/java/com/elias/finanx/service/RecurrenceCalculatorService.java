package com.elias.finanx.service;

import com.elias.finanx.entity.TransactionSchedule;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RecurrenceCalculatorService {
    Optional<OffsetDateTime> computeNextRunAt(TransactionSchedule st);
}
