package com.elias.mismonedas.billing.dto.webhook;

/** Queue envelope; the persisted webhook row remains the source of truth. */
public record PaymentWebhookMessage(Long webhookEventId) {
}
