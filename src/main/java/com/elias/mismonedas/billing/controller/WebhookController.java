package com.elias.mismonedas.billing.controller;

import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Entry point for external payment providers. Always responds fast; heavy
 * work happens inside PaymentService, keep this controller free of business logic.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Receive provider webhook",
            description = "Receives an inbound webhook notification from a payment provider.",
            operationId = "receiveWebhook",
            tags = {"Webhooks"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Accepted (duplicates are idempotent)"),
                    @ApiResponse(responseCode = "401", description = "Invalid signature")
            }
    )
    @PostMapping("/{provider}")
    public ResponseEntity<Void> receive(
            @Parameter(description = "Payment provider") @PathVariable PaymentProvider provider,
            @Parameter(description = "Signature header sent by the provider")
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawPayload) {
        log.info("Webhook received provider={}", provider);
        paymentService.processWebhookEvent(provider, rawPayload, signature);
        return ResponseEntity.ok().build();
    }
}
