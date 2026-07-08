package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.schedule.BudgetScheduleResponse;
import com.elias.mismonedas.dto.schedule.ScheduleRequest;
import com.elias.mismonedas.dto.schedule.ScheduleResponse;
import com.elias.mismonedas.dto.schedule.TransactionScheduleResponse;
import com.elias.mismonedas.entity.enums.ScheduleState;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ScheduleService {
    ScheduleResponse create(ScheduleRequest request);
    ScheduleResponse update(Long id, ScheduleRequest request);
    ScheduleResponse findById(Long id);

    List<ScheduleResponse> findAllByUser(Long userId);

    void pause(Long id);
    void resume(Long id);
    void cancel(Long id);
    void disable(Long id);

    List<ScheduleResponse> findAllActiveByUserAndState(Long userId, ScheduleState state);

    List<BudgetScheduleResponse> findAllBScheduleActiveByUserAndState(Long userId, ScheduleState state);

    List<TransactionScheduleResponse> findAllTScheduleActiveByUserAndState(Long userId, ScheduleState state);

    @Transactional
    void runDueTransactions();

    @Transactional
    void runDueBudgets();
}

