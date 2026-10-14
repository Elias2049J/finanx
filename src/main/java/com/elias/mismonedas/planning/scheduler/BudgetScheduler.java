package com.elias.mismonedas.planning.scheduler;

import com.elias.mismonedas.scheduling.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BudgetScheduler {
    private final ScheduleService service;

    @Scheduled(fixedDelay = 60_000)
    public void runScheduled() {
        service.runDueBudgets();
    }
}
