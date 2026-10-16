package com.elias.mismonedas.billing.controller;

import com.elias.mismonedas.billing.dto.payment.PaymentRequest;
import com.elias.mismonedas.billing.dto.payment.PaymentResponse;
import com.elias.mismonedas.billing.domain.enums.PaymentState;
import com.elias.mismonedas.billing.service.PaymentService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Create payment",
            description = "Starts a new payment attempt against the configured provider.",
            operationId = "createPayment",
            tags = {"Payments"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created"),
                    @ApiResponse(responseCode = "400", description = "Invalid data")
            }
    )
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @Parameter(description = "Payment data") @Valid @RequestBody PaymentRequest request) {
        PaymentResponse saved = paymentService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + saved.getId())).body(saved);
    }

    @Operation(
            summary = "Find payment",
            description = "Gets a payment by id.",
            operationId = "findPaymentById",
            tags = {"Payments"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Found"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> findById(
            @Parameter(description = "Payment id") @PathVariable Long id) {
        return ResponseEntity.ok(paymentService.findById(id));
    }

    @Operation(
            summary = "List payments by user",
            description = "Lists payments of the authenticated user with optional state, subscriptionId and from/to filters, plus pagination and sort.",
            operationId = "listPayments",
            tags = {"Payments"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "List retrieved"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @GetMapping
    public ResponseEntity<PageResponse<PaymentResponse>> listPayments(
            @RequestParam(required = false) PaymentState state,
            @RequestParam(required = false) Long subscriptionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(paymentService.list(state, subscriptionId, from, to, pageable));
    }

    @Operation(
            summary = "Refund payment",
            description = "Requests a refund on the provider and marks the payment as REFUNDED.",
            operationId = "refundPayment",
            tags = {"Payments"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Refunded"),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "409", description = "Invalid state transition")
            }
    )
    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refund(
            @Parameter(description = "Payment id") @PathVariable Long id) {
        return ResponseEntity.ok(paymentService.refund(id));
    }

}
