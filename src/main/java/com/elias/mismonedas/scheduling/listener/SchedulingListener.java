package com.elias.mismonedas.scheduling.listener;

import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletionOrder;
import com.elias.mismonedas.scheduling.repository.RecurrenceRuleRepository;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import com.elias.mismonedas.scheduling.repository.BudgetScheduleRepository;
import com.elias.mismonedas.scheduling.repository.TransactionScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchedulingListener {

    private final BudgetScheduleRepository budgetScheduleRepository;
    private final TransactionScheduleRepository transactionScheduleRepository;
    private final ScheduleRepository scheduleRepository;
    private final RecurrenceRuleRepository recurrenceRuleRepository;

    @EventListener
    @Order(UserDeletionOrder.SCHEDULING)
    public void onUserDeleted(UserDeletedEvent event) {
        budgetScheduleRepository.deleteAllByUser_Id(event.userId());
        transactionScheduleRepository.deleteAllByUser_Id(event.userId());
        scheduleRepository.deleteAllByUser_Id(event.userId());
        recurrenceRuleRepository.deleteAllByUser_Id(event.userId());
        log.info("Deleted budget and transaction schedules for user {}", event.userId());
    }
}
