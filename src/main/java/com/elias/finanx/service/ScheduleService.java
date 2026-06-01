package com.elias.finanx.service;

import com.elias.finanx.dto.schedule.ScheduleRequest;
import com.elias.finanx.dto.schedule.ScheduleResponse;
import com.elias.finanx.entity.enums.ScheduleState;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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

    List<ScheduleResponse> findAllByActiveAndState(ScheduleState state);

    @Transactional
    void runDueTransactions();

    @Transactional
    void runDueBudgets();
}

