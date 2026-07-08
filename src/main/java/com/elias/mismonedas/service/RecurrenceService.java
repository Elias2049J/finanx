package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.mismonedas.dto.recurrencerule.RecurrenceRuleResponse;
import com.elias.mismonedas.entity.RecurrenceRule;

public interface RecurrenceService {
    RecurrenceRule create(Long userId, RecurrenceRuleRequest request);

    RecurrenceRule update(Long id, RecurrenceRuleRequest request);

    RecurrenceRuleResponse findById(Long id);
}
