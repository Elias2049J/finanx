package com.elias.mismonedas.entity.enums;

/**
 * Processing outcome of an inbound {@link com.elias.mismonedas.entity.WebhookEvent}.
 */
public enum WebhookEventState {
    RECEIVED,
    PROCESSED,
    IGNORED,
    FAILED
}
