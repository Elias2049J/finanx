package com.elias.mismonedas.billing.port;

public interface PaymentWebhookPublisher {

    void publish(Long webhookEventId);
}
