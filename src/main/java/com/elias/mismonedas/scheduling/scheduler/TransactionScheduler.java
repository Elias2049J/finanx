package com.elias.mismonedas.scheduling.scheduler;

import com.elias.mismonedas.scheduling.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionScheduler {
    private final ScheduleService stService;

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "transactionScheduler.runDueTransactions", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void runScheduled() {
        stService.runDueTransactions();
    }
}
