package com.elias.mismonedas.scheduling.service;

import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.scheduling.dto.schedule.BudgetScheduleResponse;
import com.elias.mismonedas.scheduling.dto.schedule.ScheduleRequest;
import com.elias.mismonedas.scheduling.dto.schedule.ScheduleResponse;
import com.elias.mismonedas.scheduling.dto.schedule.TransactionScheduleResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
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

    PageResponse<ScheduleResponse> list(ScheduleState state, Pageable pageable);

    PageResponse<BudgetScheduleResponse> listBudgetSchedules(ScheduleState state, Pageable pageable);

    PageResponse<TransactionScheduleResponse> listTransactionSchedules(ScheduleState state, Pageable pageable);

    @Transactional
    void runDueTransactions();

    @Transactional
    void runDueBudgets();
}

