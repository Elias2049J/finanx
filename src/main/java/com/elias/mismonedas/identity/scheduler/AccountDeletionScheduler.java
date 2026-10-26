package com.elias.mismonedas.identity.scheduler;

import com.elias.mismonedas.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionScheduler {

    private final UserService userService;

    @Scheduled(cron = "0 0 4 * * ?")
    @SchedulerLock(name = "accountDeletionScheduler.processScheduledDeletions", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void processScheduledDeletions() {
        log.info("Processing scheduled account deletions");
        userService.processScheduledAccountDeletions();
    }
}
