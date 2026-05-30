package com.elias.finanx.config;

import com.elias.finanx.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponse> handleNullPointer(NullPointerException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                "Ocurrió un Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now().toString()
        );
        log.error("[handleNullPointer] Exception at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error, ex);
        return ResponseEntity.internalServerError().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception e, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(
                request.getMethod(),
                request.getRequestURI(),
                "Ocurrió un error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now().toString()
        );
        log.error("[handleAllExceptions] Exception at path: {}, method: {}, error: {}", request.getRequestURI(), request.getMethod(), error, e);
        return ResponseEntity.internalServerError().body(error);
    }
}