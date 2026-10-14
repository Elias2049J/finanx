package com.elias.mismonedas.billing.domain.enums;

/**
 * Processing outcome of an inbound {@link com.elias.mismonedas.billing.domain.entity.WebhookEvent}.
 */
public enum WebhookEventState {
    RECEIVED,
    PROCESSED,
    IGNORED,
    FAILED
}
