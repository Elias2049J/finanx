package com.elias.mismonedas.scheduling.service;

import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import com.elias.mismonedas.scheduling.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.mismonedas.scheduling.dto.recurrencerule.RecurrenceRuleResponse;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

public interface RecurrenceService {
    RecurrenceRule create(Long userId, RecurrenceRuleRequest request);

    RecurrenceRule update(Long id, RecurrenceRuleRequest request);

    RecurrenceRuleResponse findById(Long id);

    Optional<OffsetDateTime> computeNextRunAt(Schedule st);

    OffsetDateTime computeCurrentPeriodStart(RecurrenceRule rr, OffsetDateTime now, ZoneId zoneId);
}
