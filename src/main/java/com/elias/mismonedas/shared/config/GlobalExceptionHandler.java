package com.elias.mismonedas.shared.config;

import com.elias.mismonedas.shared.dto.ErrorResponse;
import com.elias.mismonedas.shared.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        int status = HttpStatus.UNAUTHORIZED.value();
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                status,
                LocalDateTime.now().toString()
        );
        log.warn("[handleAuthentication] Unauthorized at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error);
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now().toString()
        );
        log.warn("[handleIllegalArgument] Exception at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error, ex);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(UserPreferencesNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePreferencesNotFound(UserPreferencesNotFoundException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value(),
                LocalDateTime.now().toString()
        );
        log.warn("[handlePreferencesNotFound] Preferences not found at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionNotFound(SubscriptionNotFoundException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value(),
                LocalDateTime.now().toString()
        );
        log.warn("[handleSubscriptionNotFound] Subscription not found at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(CurrencyChangeRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyChangeRateLimit(CurrencyChangeRateLimitException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                LocalDateTime.now().toString()
        );
        log.warn("[handleCurrencyChangeRateLimit] at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponse> handleNullPointer(NullPointerException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                "Ocurrio un Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now().toString()
        );
        log.error("[handleNullPointer] Exception at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error, ex);
        return ResponseEntity.internalServerError().body(error);
    }

    @ExceptionHandler(OcrProcessingException.class)
    public ResponseEntity<ErrorResponse> handleOcrProcessing(OcrProcessingException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                LocalDateTime.now().toString()
        );
        log.warn("[handleOcrProcessing] at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(OcrBusyException.class)
    public ResponseEntity<ErrorResponse> handleOcrBusy(OcrBusyException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                LocalDateTime.now().toString()
        );
        log.warn("[handleOcrBusy] Exception at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "10")
                .body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                HttpStatus.FORBIDDEN.value(),
                LocalDateTime.now().toString()
        );
        log.warn("[handleAccessDenied] Forbidden at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                HttpStatus.FORBIDDEN.value(),
                LocalDateTime.now().toString()
        );
        log.warn("[handleSecurityException] Forbidden at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String message = "Violación de integridad de datos";
        if (ex.getMessage() != null && ex.getMessage().contains("uq_fcm_token_user_token")) {
            message = "El token FCM ya está registrado para este usuario";
        }
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                message,
                HttpStatus.CONFLICT.value(),
                LocalDateTime.now().toString()
        );
        log.warn("[handleDataIntegrity] Conflict at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception e, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                "Ocurrio un error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now().toString()
        );
        log.error("[handleAllExceptions] Exception at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error, e);
        return ResponseEntity.internalServerError().body(error);
    }
}
