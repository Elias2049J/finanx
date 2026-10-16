package com.elias.mismonedas.shared.exception;

public class SubscriptionNotFoundException extends RuntimeException {
    public SubscriptionNotFoundException(Long id) {
        super("Subscription not found with id " + id);
    }

    public SubscriptionNotFoundException(String message) {
        super(message);
    }
}
