package com.elias.mismonedas.service.impl.scheduler;

import com.elias.mismonedas.entity.enums.PaymentState;
import com.elias.mismonedas.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically inspects payments stuck in PENDING/PROCESSING so gaps left by
 * missed webhooks can be caught. Extend by injecting PaymentPort and querying
 * the provider's API per stuck payment to reconcile automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationScheduler {

    private final PaymentRepository paymentRepository;

    @Scheduled(fixedDelay = 900_000)
    public void reconcilePendingPayments() {
        var pending = paymentRepository.findAllByState(PaymentState.PENDING);
        var processing = paymentRepository.findAllByState(PaymentState.PROCESSING);
        if (!pending.isEmpty() || !processing.isEmpty()) {
            log.warn("Found {} pending and {} processing payments awaiting reconciliation",
                    pending.size(), processing.size());
        }
    }
}
