package com.elias.finanx.service;

import com.elias.finanx.dto.schedule.BudgetScheduleResponse;
import com.elias.finanx.dto.schedule.ScheduleRequest;
import com.elias.finanx.dto.schedule.ScheduleResponse;
import com.elias.finanx.dto.schedule.TransactionScheduleResponse;
import com.elias.finanx.entity.enums.ScheduleState;
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

