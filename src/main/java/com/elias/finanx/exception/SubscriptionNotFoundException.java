package com.elias.finanx.exception;

public class SubscriptionNotFoundException extends RuntimeException {
    public SubscriptionNotFoundException(Long id) {
        super("Subscription not found with id " + id);
    }
}
