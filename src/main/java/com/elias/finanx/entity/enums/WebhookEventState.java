package com.elias.finanx.entity.enums;

/**
 * Processing outcome of an inbound {@link com.elias.finanx.entity.WebhookEvent}.
 */
public enum WebhookEventState {
    RECEIVED,
    PROCESSED,
    IGNORED,
    FAILED
}
