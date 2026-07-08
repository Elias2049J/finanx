package com.elias.finanx.exception;

public class DuplicateWebhookEventException extends RuntimeException {
    public DuplicateWebhookEventException(String externalEventId) {
        super("Webhook event already processed: " + externalEventId);
    }
}
