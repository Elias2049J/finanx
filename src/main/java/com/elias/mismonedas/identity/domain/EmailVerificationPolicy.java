package com.elias.mismonedas.identity.domain;

import java.time.Duration;

public final class EmailVerificationPolicy {

    public static final int CODE_LENGTH = 6;
    public static final int CODE_UPPER_BOUND = 1_000_000;
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration CODE_TTL = Duration.ofMinutes(15);
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private EmailVerificationPolicy() {
    }
}
