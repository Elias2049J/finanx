package com.elias.mismonedas.billing.scheduler;

import com.elias.mismonedas.billing.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {

    private final BillingService billingService;

    @Scheduled(fixedDelay = 3_600_000)
    @SchedulerLock(name = "subscriptionScheduler.runExpirationCheck", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void runExpirationCheck() {
        log.debug("Running subscription expiration check");
        billingService.checkExpirations();
    }
}
