package com.elias.mismonedas.identity.service;

import java.time.Duration;

public interface EmailVerificationEmailSender {
    void sendVerificationCode(String email, String code, Duration validity);

    /**
     * Mensaje especifico para restablecer la contrasena. El valor por defecto
     * mantiene compatibles las implementaciones existentes.
     */
    default void sendPasswordResetCode(String email, String code, Duration validity) {
        sendVerificationCode(email, code, validity);
    }
}
