package com.elias.mismonedas.billing.adapter.payment;

import com.elias.mismonedas.billing.domain.entity.Payment;
import com.elias.mismonedas.billing.dto.webhook.PaymentWebhookNotification;
import com.elias.mismonedas.billing.port.PaymentPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ManualPaymentGatewayAdapter implements PaymentPort {

    @Override
    public String createPaymentIntent(Payment payment) {
        log.info("Creating manual payment intent for payment id={}", payment.getId());
        return "MANUAL-" + payment.getId() + "-" + System.currentTimeMillis();
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signatureHeader) {
        log.warn("Manual provider does not receive webhooks; rejecting signature check");
        return false;
    }

    @Override
    public PaymentWebhookNotification parseWebhookPayload(String rawPayload) {
        throw new UnsupportedOperationException("Manual provider does not send webhooks");
    }

    @Override
    public void refund(Payment payment) {
        log.info("Manual refund must be handled operationally for payment id={}", payment.getId());
    }
}
