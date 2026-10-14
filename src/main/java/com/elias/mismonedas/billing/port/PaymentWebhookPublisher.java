package com.elias.mismonedas.billing.port;

/** Publishes a durable webhook-event reference for asynchronous processing. */
public interface PaymentWebhookPublisher {

    void publish(Long webhookEventId);
}
