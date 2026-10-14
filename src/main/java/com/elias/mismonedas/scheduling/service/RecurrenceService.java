package com.elias.mismonedas.scheduling.service;

import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.mismonedas.scheduling.dto.recurrencerule.RecurrenceRuleResponse;

public interface RecurrenceService {
    RecurrenceRule create(Long userId, RecurrenceRuleRequest request);

    RecurrenceRule update(Long id, RecurrenceRuleRequest request);

    RecurrenceRuleResponse findById(Long id);
}
