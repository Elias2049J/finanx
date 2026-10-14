package com.elias.mismonedas.shared.adapter.ai.gemini.exception;

import lombok.Getter;

/**
 * Excepcion base para errores de la Gemini Interactions API.
 */
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


    /** 429 — Free tier o cuota agotada. */
    public static class RateLimitException extends GeminiException {
        public RateLimitException(String message) { super(message, 429); }
    }

    /** 503 / 502 — Servicio no disponible temporalmente. */
    public static class UnavailableException extends GeminiException {
        public UnavailableException(String message) { super(message, 503); }
    }

    /** 400 — Request mal formado (modelo no valido, campos faltantes, etc.). */
    public static class InvalidRequestException extends GeminiException {
        public InvalidRequestException(String message) { super(message, 400); }
    }

    /** 401 / 403 — API key invalida o sin permisos. */
    public static class AuthException extends GeminiException {
        public AuthException(String message) { super(message, 401); }
    }
}