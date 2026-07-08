package com.elias.mismonedas.service;

import com.elias.mismonedas.entity.RecurrenceRule;
import com.elias.mismonedas.entity.Schedule;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

public interface RecurrenceCalculatorService {
    Optional<OffsetDateTime> computeNextRunAt(Schedule st);

    OffsetDateTime computeCurrentPeriodStart(RecurrenceRule rr, OffsetDateTime now, ZoneId zoneId);
}
