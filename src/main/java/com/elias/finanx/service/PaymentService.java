package com.elias.finanx.service;

import com.elias.finanx.dto.payment.PaymentRequest;
import com.elias.finanx.dto.payment.PaymentResponse;
import com.elias.finanx.dto.payment.Receipt;
import com.elias.finanx.entity.enums.PaymentProvider;

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

    Receipt getReceiptById(Long paymentId);

    List<Receipt> findAllReceiptsByUser(Long userId);
}
