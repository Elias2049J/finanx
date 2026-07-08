package com.elias.mismonedas.service.impl.scheduler;

import com.elias.mismonedas.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BudgetScheduler {
    private final ScheduleService service;

    @Scheduled(fixedDelay = 60_000)
    private void runScheduled() {
        service.runDueBudgets();
    }
}
