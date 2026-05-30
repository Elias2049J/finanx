package com.elias.finanx.service.impl.scheduler;

import com.elias.finanx.service.TransactionScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionScheduler {
    private final TransactionScheduleService stService;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void runScheduled() {
        stService.runAllDueUpTo(OffsetDateTime.now());
    }
}
