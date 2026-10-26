package com.elias.mismonedas.identity.service;

public class VerificationCodeCooldownException extends RuntimeException {

    private final long retryAfterSeconds;

    public VerificationCodeCooldownException(long retryAfterSeconds) {
        super("A verification code was sent recently");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
