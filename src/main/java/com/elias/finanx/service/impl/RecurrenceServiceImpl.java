package com.elias.finanx.service.impl;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.finanx.dto.recurrencerule.RecurrenceRuleResponse;
import com.elias.finanx.entity.RecurrenceRule;
import com.elias.finanx.mapper.RecurrenceRuleMapper;
import com.elias.finanx.repository.RecurrenceRuleRepository;
import com.elias.finanx.service.RecurrenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecurrenceServiceImpl implements RecurrenceService {
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final RecurrenceRuleMapper recurrenceRuleMapper;

    @Override
    @Transactional
    public RecurrenceRule create(RecurrenceRuleRequest request) {
        RecurrenceRule recurrenceRule = recurrenceRuleMapper.toEntity(request);
        return recurrenceRuleRepository.save(recurrenceRule);
    }

    @Override
    public RecurrenceRuleResponse update(Long id, RecurrenceRuleRequest request) {
        RecurrenceRule existing = recurrenceRuleRepository.findById(id).orElseThrow();
        recurrenceRuleMapper.updateFromDto(request, existing);
        return recurrenceRuleMapper.toResponse(recurrenceRuleRepository.save(existing));
    }

    @Override
    public RecurrenceRuleResponse findById(Long id) {
        return recurrenceRuleMapper.toResponse(recurrenceRuleRepository.findById(id).orElseThrow());
    }
}
