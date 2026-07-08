package com.elias.mismonedas.service.impl.scheduler;

import com.elias.mismonedas.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionScheduler {
    private final ScheduleService stService;

    @Scheduled(fixedDelay = 60_000)
    public void runScheduled() {
        stService.runDueTransactions();
    }
}
