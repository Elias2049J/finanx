package com.elias.mismonedas.shared.adapter.ai.gemini.exception;

import lombok.Getter;



@Getter
public class GeminiException extends RuntimeException {

    private final int httpStatus;

    public GeminiException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public GeminiException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }


    public static class RateLimitException extends GeminiException {
        public RateLimitException(String message) { super(message, 429); }
    }

    public static class UnavailableException extends GeminiException {
        public UnavailableException(String message) { super(message, 503); }
    }

    public static class InvalidRequestException extends GeminiException {
        public InvalidRequestException(String message) { super(message, 400); }
    }

    public static class AuthException extends GeminiException {
        public AuthException(String message) { super(message, 401); }
    }
}