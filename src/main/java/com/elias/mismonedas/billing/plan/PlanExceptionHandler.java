package com.elias.mismonedas.billing.plan;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlanExceptionHandler {

    @ExceptionHandler(PlanRequiredException.class)
    public ResponseEntity<PlanErrorResponse> handle(PlanRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new PlanErrorResponse("PLAN_REQUIRED", ex.getMessage(), ex.getRequired(), ex.getCurrent()));
    }
}
