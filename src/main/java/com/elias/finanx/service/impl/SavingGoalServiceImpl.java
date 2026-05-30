package com.elias.finanx.service.impl;

import com.elias.finanx.dto.saving.SavingGoalRequest;
import com.elias.finanx.dto.saving.SavingGoalResponse;
import com.elias.finanx.entity.SavingGoal;
import com.elias.finanx.entity.User;
import com.elias.finanx.mapper.SavingGoalMapper;
import com.elias.finanx.repository.SavingGoalRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.SavingGoalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class SavingGoalServiceImpl implements SavingGoalService {
    private final SavingGoalRepository sgRepository;
    private final SavingGoalMapper sgMapper;
    private final UserRepository userRepository;
    
    @Override
    @Transactional
    public SavingGoalResponse create(SavingGoalRequest request) {
        SavingGoal sg = sgMapper.toEntity(request);

        User user = userRepository.getReferenceById(request.getUserId());
        sg.setUser(user);

        ZoneId userZone = user.getTimeZone().toZoneId();
        sg.setDeadline(request.getDeadline().atZone(userZone).toOffsetDateTime());

        sg.setCreatedAt(OffsetDateTime.now(userZone));
        sg.setActive(true);

        return sgMapper.toResponse(sgRepository.save(sg));
    }

    @Override
    @Transactional
    public SavingGoalResponse update(Long id, SavingGoalRequest request) {
        SavingGoal existing = sgRepository.findById(id).orElseThrow();

        existing.setDescription(request.getDescription());
        existing.setTargetAmount(request.getTargetAmount());

        ZoneId userZone = existing.getUser().getTimeZone().toZoneId();
        existing.setDeadline(request.getDeadline().atZone(userZone).toOffsetDateTime());

        return sgMapper.toResponse(sgRepository.save(existing));
    }

    @Override
    @Transactional
    public void disable(Long id) {
        SavingGoal existing = sgRepository.findById(id).orElseThrow();
        existing.setActive(false);
        sgRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavingGoalResponse> findAllActiveByUser(Long userId) {
        return sgRepository.findAllByUser_IdAndActive(userId, true)
                .stream()
                .map(sgMapper::toResponse)
                .toList();
    }

    @Override
    public SavingGoalResponse findById(Long id) {
        return sgMapper.toResponse(sgRepository.findById(id).orElseThrow());
    }
}
