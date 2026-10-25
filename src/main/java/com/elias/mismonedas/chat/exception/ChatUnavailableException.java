package com.elias.mismonedas.chat.exception;

public class ChatUnavailableException extends RuntimeException {

    public ChatUnavailableException() {
        super("Chat service is temporarily unavailable");
    }
}
