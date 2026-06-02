package com.elias.finanx.service;

import com.elias.finanx.dto.saving.SavingGoalRequest;
import com.elias.finanx.dto.saving.SavingGoalResponse;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SavingGoalService {
    SavingGoalResponse create(SavingGoalRequest request);
    SavingGoalResponse update(Long id, SavingGoalRequest request);
    void disable(Long id);
    List<SavingGoalResponse> findAllActiveByUser(Long userId);
    SavingGoalResponse findById(Long id);

    @Transactional
    void checkAllSavingGoals(Long userId);
}
