package com.elias.mismonedas.shared.config;

import com.elias.mismonedas.shared.dto.ErrorResponse;
import com.elias.mismonedas.shared.exception.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

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

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, ex);
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

    @ExceptionHandler(CurrencyConversionException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyConversion(CurrencyConversionException ex, HttpServletRequest request) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, ex);
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

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        int status = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value();
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(),
                status,
                LocalDateTime.now().toString()
        );
        log.warn("[handleMediaTypeNotSupported] Unsupported media type at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error);
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNoSuchElement(NoSuchElementException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "Recurso no encontrado", request, ex);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), request, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        return respond(HttpStatus.BAD_REQUEST, message.isBlank() ? "Parametros invalidos" : message, request, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, fieldErrorsOf(ex.getBindingResult()), request, ex);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, fieldErrorsOf(ex.getBindingResult()), request, ex);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Solicitud invalida: " + ex.getMessage(), request, ex);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request, ex);
    }

    private String fieldErrorsOf(BindingResult bindingResult) {
        String message = bindingResult.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return message.isBlank() ? "Datos de entrada invalidos" : message;
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String message, HttpServletRequest request, Exception ex) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                message,
                status.value(),
                LocalDateTime.now().toString()
        );
        if (status.is5xxServerError()) {
            log.error("[{}] Exception at path: {}, method: {}, error: {}", status.value(), request.getRequestURI(), request.getMethod(), error, ex);
        } else {
            log.warn("[{}] Rejected at path: {}, method: {}, error: {}", status.value(), request.getRequestURI(), request.getMethod(), error);
        }
        return ResponseEntity.status(status).body(error);
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
