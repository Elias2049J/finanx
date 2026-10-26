package com.elias.mismonedas.notifications.adapter.email;

public class EmailTransportException extends RuntimeException {
    public EmailTransportException(Throwable cause) {
        super("Error enviando email por SMTP", cause);
    }
}
