package com.elias.mismonedas.scheduling.listener;

import com.elias.mismonedas.ledger.service.TransactionRecorder;
import com.elias.mismonedas.scheduling.domain.event.TransactionScheduleDueEvent;
import com.elias.mismonedas.scheduling.domain.event.TransactionScheduleExecutedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionScheduleListener {

    private final TransactionRecorder transactionRecorder;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void on(TransactionScheduleDueEvent event) {
        transactionRecorder.record(
                event.userId(),
                event.walletId(),
                event.categoryId(),
                event.reasonId(),
                event.scheduleId(),
                event.amount(),
                event.type(),
                event.executedAt(),
                null,
                null,
                null,
                null);

        eventPublisher.publishEvent(new TransactionScheduleExecutedEvent(event.scheduleId(), event.userId()));
    }
}
