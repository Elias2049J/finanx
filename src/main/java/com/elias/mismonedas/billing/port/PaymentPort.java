package com.elias.mismonedas.billing.port;

import com.elias.mismonedas.billing.domain.entity.Payment;
import com.elias.mismonedas.billing.dto.webhook.PaymentWebhookNotification;

/**
 * Abstraction over an external payment gateway (MercadoPago, Culqi, Stripe,
 * etc). PaymentServiceImpl depends only on this port, never on a specific
 * gateway SDK, so new providers can be added without touching service logic.
 */
public interface PaymentPort {

    /** Starts a payment on the external gateway and returns its external reference. */
    String createPaymentIntent(Payment payment);

    /** Validates that an inbound webhook truly comes from the provider. */
    boolean verifyWebhookSignature(String rawPayload, String signatureHeader);

    /** Parses a raw webhook payload into a normalized notification. */
    PaymentWebhookNotification parseWebhookPayload(String rawPayload);

    /** Requests a refund on the external gateway for a completed payment. */
    void refund(Payment payment);
}
