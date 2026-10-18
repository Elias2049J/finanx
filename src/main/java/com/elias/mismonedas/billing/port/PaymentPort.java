package com.elias.mismonedas.billing.port;

import com.elias.mismonedas.billing.domain.entity.Payment;
import com.elias.mismonedas.billing.dto.webhook.PaymentWebhookNotification;





public interface PaymentPort {

    String createPaymentIntent(Payment payment);

    boolean verifyWebhookSignature(String rawPayload, String signatureHeader);

    PaymentWebhookNotification parseWebhookPayload(String rawPayload);

    void refund(Payment payment);
}
