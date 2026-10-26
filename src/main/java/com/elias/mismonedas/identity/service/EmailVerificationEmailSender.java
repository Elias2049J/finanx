package com.elias.mismonedas.identity.service;

import java.time.Duration;

public interface EmailVerificationEmailSender {
    void sendVerificationCode(String email, String code, Duration validity);
}
