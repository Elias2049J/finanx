package com.elias.finanx.service;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.finanx.dto.recurrencerule.RecurrenceRuleResponse;
import com.elias.finanx.entity.RecurrenceRule;

public interface RecurrenceService {
    RecurrenceRule create(Long userId, RecurrenceRuleRequest request);

    RecurrenceRule update(Long id, RecurrenceRuleRequest request);

    RecurrenceRuleResponse findById(Long id);
}
