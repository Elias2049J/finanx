package com.elias.mismonedas.billing.scheduler;

import com.elias.mismonedas.billing.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically re-drives inbound webhook events left {@code RECEIVED} (a crash
 * between persisting and processing) or {@code FAILED} (a transient error) by
 * replaying their stored raw payload. The work itself lives in
 * {@link PaymentService#reconcileWebhookEvents()} and is idempotent, so this job
 * only schedules it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationScheduler {

    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 900_000)
    public void reconcilePendingPayments() {
        int reconciled = paymentService.reconcileWebhookEvents();
        if (reconciled > 0) {
            log.info("Reconciled {} webhook events", reconciled);
        }
    }
}
