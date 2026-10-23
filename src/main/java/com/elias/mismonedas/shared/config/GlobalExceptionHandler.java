package com.elias.mismonedas.shared.config;

import com.elias.mismonedas.shared.dto.ErrorResponse;
import com.elias.mismonedas.shared.exception.*;
import com.elias.mismonedas.shared.i18n.CurrentLocaleProvider;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final JsonMessageCatalog messageCatalog;
    private final CurrentLocaleProvider localeProvider;

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "error.authentication_required", request, ex);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "error.refresh_token_invalid", request, ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "error.invalid_argument", request, ex);
    }

    @ExceptionHandler(UserPreferencesNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePreferencesNotFound(UserPreferencesNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "USER_PREFERENCES_NOT_FOUND", "error.preferences_not_found", request, ex);
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionNotFound(SubscriptionNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "SUBSCRIPTION_NOT_FOUND", "error.subscription_not_found", request, ex);
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponse> handleNullPointer(NullPointerException ex, HttpServletRequest request) {
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "error.internal", request, ex);
    }

    @ExceptionHandler(OcrProcessingException.class)
    public ResponseEntity<ErrorResponse> handleOcrProcessing(OcrProcessingException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "OCR_PROCESSING_FAILED", "error.ocr_processing", request, ex);
    }

    @ExceptionHandler(OcrBusyException.class)
    public ResponseEntity<ErrorResponse> handleOcrBusy(OcrBusyException ex, HttpServletRequest request) {
        ErrorResponse error = errorResponse(
                HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_BUSY", "error.service_busy", request, ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "10")
                .body(error);
    }

    @ExceptionHandler(CurrencyConversionException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyConversion(CurrencyConversionException ex, HttpServletRequest request) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "CURRENCY_CONVERSION_UNAVAILABLE", "error.currency_conversion", request, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "error.access_denied", request, ex);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "SECURITY_ERROR", "error.access_denied", request, ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        boolean duplicateFcmToken = ex.getMessage() != null
                && ex.getMessage().contains("uq_fcm_token_user_token");
        return duplicateFcmToken
                ? respond(HttpStatus.CONFLICT, "FCM_TOKEN_ALREADY_REGISTERED", "error.fcm_token_duplicate", request, ex)
                : respond(HttpStatus.CONFLICT, "DATA_CONFLICT", "error.data_conflict", request, ex);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "error.unsupported_media_type", request, ex);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "error.not_found", request, ex);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "error.payment_not_found", request, ex);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNoSuchElement(NoSuchElementException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "error.not_found", request, ex);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "CONFLICT", "error.conflict", request, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        String fields = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath().toString())
                .distinct()
                .collect(Collectors.joining(", "));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "error.validation", request, ex, fields);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "error.validation", request, ex,
                fieldNamesOf(ex.getBindingResult()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "error.validation", request,
                ex, fieldNamesOf(ex.getBindingResult()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "error.bad_request", request, ex);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "error.method_not_allowed", request, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex, HttpServletRequest request) {
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "error.internal", request, ex);
    }

    private String fieldNamesOf(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(error -> error.getField())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private ResponseEntity<ErrorResponse> respond(
            HttpStatus status,
            String code,
            String messageKey,
            HttpServletRequest request,
            Exception ex,
            Object... arguments
    ) {
        ErrorResponse error = errorResponse(status, code, messageKey, request, ex, arguments);
        return ResponseEntity.status(status).body(error);
    }

    private ErrorResponse errorResponse(
            HttpStatus status,
            String code,
            String messageKey,
            HttpServletRequest request,
            Exception ex,
            Object... arguments
    ) {
        String message = messageCatalog.getMessage(messageKey, localeProvider.currentLocale(), arguments);
        ErrorResponse error = new ErrorResponse(
                code,
                request.getMethod(),
                request.getRequestURI(),
                message,
                status.value(),
                LocalDateTime.now().toString()
        );
        if (status.is5xxServerError()) {
            log.error("[{}] Exception at path: {}, method: {}, code: {}", status.value(), request.getRequestURI(), request.getMethod(), code, ex);
        } else {
            log.warn("[{}] Rejected at path: {}, method: {}, code: {}", status.value(), request.getRequestURI(), request.getMethod(), code);
        }
        return error;
    }
}
