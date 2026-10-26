package com.elias.mismonedas.identity.service;

public class VerificationCodeRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public VerificationCodeRateLimitException(long retryAfterSeconds) {
        super("Global email verification send limit reached");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
