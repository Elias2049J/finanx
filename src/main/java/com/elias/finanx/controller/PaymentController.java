package com.elias.finanx.controller;

import com.elias.finanx.dto.payment.PaymentRequest;
import com.elias.finanx.dto.payment.PaymentResponse;
import com.elias.finanx.dto.payment.Receipt;
import com.elias.finanx.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/payments")
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
        return ResponseEntity.created(URI.create("/payments/" + saved.getId())).body(saved);
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
            description = "Gets all payments made by a user.",
            operationId = "findAllPaymentsByUser",
            tags = {"Payments"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "List retrieved"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PaymentResponse>> findAllByUser(
            @Parameter(description = "User id") @PathVariable Long userId) {
        return ResponseEntity.ok(paymentService.findAllByUser(userId));
    }

    @Operation(
            summary = "List payments by subscription",
            description = "Gets all payments linked to a subscription.",
            operationId = "findAllPaymentsBySubscription",
            tags = {"Payments"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "List retrieved")
            }
    )
    @GetMapping("/subscription/{subscriptionId}")
    public ResponseEntity<List<PaymentResponse>> findAllBySubscription(
            @Parameter(description = "Subscription id") @PathVariable Long subscriptionId) {
        return ResponseEntity.ok(paymentService.findAllBySubscription(subscriptionId));
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

    @Operation(
            summary = "Get receipt",
            description = "Gets the receipt for a completed payment.",
            operationId = "getReceipt",
            tags = {"Payments"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Receipt found"),
                    @ApiResponse(responseCode = "404", description = "Payment not found or not completed")
            }
    )
    @GetMapping("/{id}/receipt")
    public ResponseEntity<Receipt> getReceipt(
            @Parameter(description = "Payment id") @PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getReceiptById(id));
    }

    @Operation(
            summary = "List receipts by user",
            description = "Gets all receipts for completed payments of a user.",
            operationId = "findAllReceiptsByUser",
            tags = {"Payments"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "List retrieved"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @GetMapping("/user/{userId}/receipts")
    public ResponseEntity<List<Receipt>> findAllReceiptsByUser(
            @Parameter(description = "User id") @PathVariable Long userId) {
        return ResponseEntity.ok(paymentService.findAllReceiptsByUser(userId));
    }
}
