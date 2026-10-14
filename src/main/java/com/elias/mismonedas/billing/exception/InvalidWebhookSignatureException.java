package com.elias.mismonedas.billing.exception;

import org.springframework.security.core.AuthenticationException;

public class InvalidWebhookSignatureException extends AuthenticationException {

    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
