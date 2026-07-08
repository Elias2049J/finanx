package com.elias.mismonedas.service.impl.scheduler;

import com.elias.mismonedas.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {

    private final BillingService billingService;

    @Scheduled(fixedDelay = 3_600_000)
    public void runExpirationCheck() {
        log.debug("Running subscription expiration check");
        billingService.checkExpirations();
    }
}
