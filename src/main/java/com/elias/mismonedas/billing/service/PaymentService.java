package com.elias.mismonedas.billing.service;

import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.domain.enums.PaymentState;
import com.elias.mismonedas.billing.dto.payment.PaymentRequest;
import com.elias.mismonedas.billing.dto.payment.PaymentResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Business operations for payments and inbound provider webhooks.
 */
public interface PaymentService {

    PaymentResponse create(PaymentRequest request);

    PaymentResponse findById(Long id);

    List<PaymentResponse> findAllByUser(Long userId);

    PageResponse<PaymentResponse> list(PaymentState state, Long subscriptionId, LocalDate from, LocalDate to, Pageable pageable);

    List<PaymentResponse> findAllBySubscription(Long subscriptionId);

    PaymentResponse markCompleted(Long paymentId);

    PaymentResponse markFailed(Long paymentId, String reason);

    PaymentResponse refund(Long paymentId);

    /** Verifies and durably records an inbound notification before publishing its id. */
    void receiveWebhookEvent(PaymentProvider provider, String rawPayload, String signatureHeader);

    /** Processes one persisted webhook event; transient failures are propagated for broker retry. */
    void processWebhookEvent(Long webhookEventId);

    /** Marks an event FAILED after the broker has exhausted its retry attempts. */
    void markWebhookEventFailed(Long webhookEventId, String errorMessage);

    /**
     * Republishes stale {@code RECEIVED} events left behind by a process or
     * broker failure. Failed events are retained in the DLQ for explicit redrive.
     *
     * @return number of events successfully handed to the broker
     */
    int reconcileWebhookEvents();
}
