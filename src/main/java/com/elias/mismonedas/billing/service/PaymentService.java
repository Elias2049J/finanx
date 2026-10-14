package com.elias.mismonedas.billing.service;

import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.dto.payment.PaymentRequest;
import com.elias.mismonedas.billing.dto.payment.PaymentResponse;

import java.util.List;

/**
 * Business operations for payments and inbound provider webhooks.
 */
public interface PaymentService {

    PaymentResponse create(PaymentRequest request);

    PaymentResponse findById(Long id);

    List<PaymentResponse> findAllByUser(Long userId);

    List<PaymentResponse> findAllBySubscription(Long subscriptionId);

    PaymentResponse markCompleted(Long paymentId);

    PaymentResponse markFailed(Long paymentId, String reason);

    PaymentResponse refund(Long paymentId);

    /** Verifies, deduplicates and applies an inbound webhook notification. */
    void processWebhookEvent(PaymentProvider provider, String rawPayload, String signatureHeader);

    /**
     * Re-drives webhook events left {@code RECEIVED} (stuck) or {@code FAILED}
     * (transient) by replaying their stored payload. Idempotent.
     *
     * @return number of events successfully reconciled
     */
    int reconcileWebhookEvents();
}
