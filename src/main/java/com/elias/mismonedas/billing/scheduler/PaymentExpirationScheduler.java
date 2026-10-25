package com.elias.mismonedas.billing.scheduler;

import com.elias.mismonedas.billing.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentExpirationScheduler {

    private final PaymentService paymentService;

    @Value("${app.billing.payment-expiration.pending-minutes:1440}")
    private long pendingMinutes;

    @Value("${app.billing.payment-expiration.processing-minutes:60}")
    private long processingMinutes;

    @Scheduled(fixedDelay = 900_000)
    @SchedulerLock(name = "paymentExpirationScheduler.expireStalePayments", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void expireStalePayments() {
        log.debug("Running payment expiration check");
        OffsetDateTime now = OffsetDateTime.now();
        paymentService.expireStalePayments(
                now.minusMinutes(pendingMinutes),
                now.minusMinutes(processingMinutes));
    }
}
