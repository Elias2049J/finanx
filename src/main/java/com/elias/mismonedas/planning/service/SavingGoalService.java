package com.elias.mismonedas.planning.service;

import com.elias.mismonedas.planning.dto.saving.SavingGoalRequest;
import com.elias.mismonedas.planning.dto.saving.SavingGoalResponse;
import com.elias.mismonedas.planning.domain.enums.SavingGoalState;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
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
    PageResponse<SavingGoalResponse> list(Boolean active, SavingGoalState state, Pageable pageable);
    SavingGoalResponse findById(Long id);

    @Transactional
    void checkAllSavingGoals(Long userId);
}
