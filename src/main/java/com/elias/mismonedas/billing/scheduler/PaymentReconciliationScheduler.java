package com.elias.mismonedas.billing.scheduler;

import com.elias.mismonedas.billing.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically republishes webhook events left {@code RECEIVED} after a crash
 * or broker outage. Processing and transient retries happen asynchronously in
 * the RabbitMQ consumer; exhausted messages stay in the DLQ for redrive.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationScheduler {

    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 900_000)
    @SchedulerLock(name = "paymentReconciliationScheduler.reconcilePendingPayments", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void reconcilePendingPayments() {
        int reconciled = paymentService.reconcileWebhookEvents();
        if (reconciled > 0) {
            log.info("Republished {} stale webhook events", reconciled);
        }
    }
}
