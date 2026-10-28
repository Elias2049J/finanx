package com.elias.mismonedas.scheduling.service;

import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.scheduling.dto.schedule.BudgetScheduleResponse;
import com.elias.mismonedas.scheduling.dto.schedule.OccurrenceConfirmationResponse;
import com.elias.mismonedas.scheduling.dto.schedule.OccurrenceKeyRequest;
import com.elias.mismonedas.scheduling.dto.schedule.OccurrenceResponse;
import com.elias.mismonedas.scheduling.dto.schedule.ScheduleRequest;
import com.elias.mismonedas.scheduling.dto.schedule.ScheduleResponse;
import com.elias.mismonedas.scheduling.dto.schedule.TransactionScheduleResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleService {
    ScheduleResponse create(ScheduleRequest request);
    ScheduleResponse findById(Long id);

    void pause(Long id);
    void resume(Long id);
    void cancel(Long id);
    void disable(Long id);

    OccurrenceConfirmationResponse confirm(Long id, OccurrenceKeyRequest request);

    void ignore(Long id, OccurrenceKeyRequest request);

    PageResponse<ScheduleResponse> list(ScheduleState state, Pageable pageable);

    PageResponse<BudgetScheduleResponse> listBudgetSchedules(ScheduleState state, Pageable pageable);

    PageResponse<TransactionScheduleResponse> listTransactionSchedules(ScheduleState state, Pageable pageable);

    List<OccurrenceResponse> listOccurrences(LocalDate from, LocalDate to);

    @Transactional
    void runDueTransactions();

    @Transactional
    void runDueBudgets();
}

