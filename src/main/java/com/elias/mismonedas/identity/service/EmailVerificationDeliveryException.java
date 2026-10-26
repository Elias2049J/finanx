package com.elias.mismonedas.identity.service;

public class EmailVerificationDeliveryException extends RuntimeException {
    public EmailVerificationDeliveryException(Throwable cause) {
        super("Unable to deliver email verification code", cause);
    }
}
