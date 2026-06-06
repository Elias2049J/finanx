package com.elias.finanx.service.impl;

import com.elias.finanx.dto.recurrencerule.RecurrenceRuleRequest;
import com.elias.finanx.dto.recurrencerule.RecurrenceRuleResponse;
import com.elias.finanx.entity.RecurrenceRule;
import com.elias.finanx.entity.User;
import com.elias.finanx.mapper.RecurrenceRuleMapper;
import com.elias.finanx.repository.RecurrenceRuleRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.RecurrenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class RecurrenceServiceImpl implements RecurrenceService {
    private final RecurrenceRuleRepository recurrenceRuleRepository;
    private final RecurrenceRuleMapper recurrenceRuleMapper;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RecurrenceRule create(Long userId, RecurrenceRuleRequest request) {
        RecurrenceRule recurrenceRule = recurrenceRuleMapper.toEntity(request);

        User user = userRepository.getReferenceById(userId);
        recurrenceRule.setUser(user);

        ZoneId userZone = user.getTimeZone().toZoneId();
        recurrenceRule.setStart(request.getStart().atStartOfDay().atZone(userZone).toOffsetDateTime());
        recurrenceRule.setEnd(request.getEnd().atStartOfDay().atZone(userZone).toOffsetDateTime());
        return recurrenceRuleRepository.save(recurrenceRule);
    }

    @Override
    public RecurrenceRule update(Long id, RecurrenceRuleRequest request) {
        RecurrenceRule existing = recurrenceRuleRepository.findById(id).orElseThrow();
        recurrenceRuleMapper.updateFromDto(request, existing);

        ZoneId userZone = existing.getUser().getTimeZone().toZoneId();

        existing.setStart(request.getStart().atStartOfDay().atZone(userZone).toOffsetDateTime());
        existing.setEnd(request.getEnd().atStartOfDay().atZone(userZone).toOffsetDateTime());

        return recurrenceRuleRepository.save(existing);
    }

    @Override
    public RecurrenceRuleResponse findById(Long id) {
        return recurrenceRuleMapper.toResponse(recurrenceRuleRepository.findById(id).orElseThrow());
    }
}
