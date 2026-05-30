package com.elias.finanx.service;

import com.elias.finanx.dto.scheduledtransaction.TransactionScheduleRequest;
import com.elias.finanx.dto.scheduledtransaction.TransactionScheduleResponse;
import com.elias.finanx.entity.enums.TransactionState;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

public interface TransactionScheduleService {
    TransactionScheduleResponse create(TransactionScheduleRequest request);
    TransactionScheduleResponse update(Long id, TransactionScheduleRequest request);
    TransactionScheduleResponse findById(Long id);

    List<TransactionScheduleResponse> findAllByUser(Long userId);

    @Transactional
    void runAllDueUpTo(OffsetDateTime upTo);

    void pause(Long id);
    void resume(Long id);
    void cancel(Long id);
    void disable(Long id);

    List<TransactionScheduleResponse> findAllByActiveAndState(TransactionState state);
}

