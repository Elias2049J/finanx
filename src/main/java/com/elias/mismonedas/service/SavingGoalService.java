package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.saving.SavingGoalRequest;
import com.elias.mismonedas.dto.saving.SavingGoalResponse;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SavingGoalService {
    SavingGoalResponse create(SavingGoalRequest request);
    SavingGoalResponse update(Long id, SavingGoalRequest request);

    @Transactional
    void cancel(Long id);

    @Transactional
    void disable(Long id);
    List<SavingGoalResponse> findAllActiveByUser(Long userId);
    SavingGoalResponse findById(Long id);

    @Transactional
    void checkAllSavingGoals(Long userId);
}
