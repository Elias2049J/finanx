package com.elias.finanx.service;

import com.elias.finanx.entity.RecurrenceRule;
import com.elias.finanx.entity.Schedule;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

public interface RecurrenceCalculatorService {
    Optional<OffsetDateTime> computeNextRunAt(Schedule st);

    OffsetDateTime computeCurrentPeriodStart(RecurrenceRule rr, OffsetDateTime now, ZoneId zoneId);
}
